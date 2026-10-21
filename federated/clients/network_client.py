"""
AeroSentinel - Federated Network Client Adapter
File: federated/clients/network_client.py

Provides robust, typed HTTP REST communication between Python municipal nodes / coordinator
and the Spring Boot Federated Control Plane running at http://localhost:8080.
Adheres strictly to the F9-P1 payload schema and F9-P4 REST contracts.
"""

from typing import Dict, List, Optional, Any, Union
import logging
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

logger = logging.getLogger(__name__)


class FederatedNetworkError(Exception):
    """Base exception for federated network communication errors."""

    def __init__(self, message: str, status_code: Optional[int] = None, response_body: Optional[Any] = None):
        super().__init__(message)
        self.status_code = status_code
        self.response_body = response_body


class ValidationError(FederatedNetworkError):
    """Raised when request payload or state validation fails (HTTP 400)."""
    pass


class DuplicateUpdateError(FederatedNetworkError):
    """Raised when a node submits multiple updates to the same round (HTTP 409 Conflict)."""
    pass


class QuorumNotMetError(ValidationError):
    """Raised when aggregation is triggered but minimum quorum is unmet (HTTP 400)."""
    pass


class ResourceNotFoundError(FederatedNetworkError):
    """Raised when a round, node, or model cannot be found (HTTP 404 Not Found)."""
    pass


class FederatedNetworkClient:
    """
    HTTP REST Client adapter for communicating with the AeroSentinel Spring Boot
    Federated Control Plane (/api/v1/federated/*).
    """

    def __init__(
        self,
        base_url: str = "http://localhost:8080",
        timeout: float = 10.0,
        max_retries: int = 3,
        backoff_factor: float = 0.3,
        auth_token: Optional[str] = None,
    ):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.auth_token = auth_token

        # Session with connection pooling and transient retry logic
        self.session = requests.Session()
        if auth_token:
            self.session.headers["Authorization"] = f"Bearer {auth_token}"
        retries = Retry(
            total=max_retries,
            backoff_factor=backoff_factor,
            status_forcelist=[502, 503, 504],
            allowed_methods=["HEAD", "GET", "OPTIONS"],
        )
        adapter = HTTPAdapter(max_retries=retries)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)

    def login(
        self,
        email: str = "admin@aerosentinel.gov",
        password: str = "password",
    ) -> str:
        """
        Authenticates with Spring Boot auth service and configures Authorization Bearer header.
        """
        url = self._url("/api/v1/auth/login")
        payload = {"email": email, "password": password}
        res = self.session.post(url, json=payload, timeout=self.timeout)
        data = self._handle_response(res)
        token = data.get("token")
        if token:
            self.auth_token = token
            self.session.headers["Authorization"] = f"Bearer {token}"
            return token
        raise FederatedNetworkError("Login succeeded but no token was returned", response_body=data)

    def _url(self, path: str) -> str:
        """Constructs absolute URL from endpoint path."""
        return f"{self.base_url}/{path.lstrip('/')}"

    def _handle_response(self, response: requests.Response) -> Any:
        """
        Parses response JSON and maps HTTP error status codes to typed exceptions.
        """
        try:
            body = response.json() if response.content else {}
        except Exception:
            body = {"raw": response.text}

        if response.status_code >= 400:
            error_msg = ""
            if isinstance(body, dict):
                error_msg = body.get("message") or body.get("error") or str(body)
            else:
                error_msg = str(body)

            if response.status_code == 409 or "duplicate" in error_msg.lower():
                raise DuplicateUpdateError(
                    f"HTTP 409 Conflict: {error_msg}",
                    status_code=response.status_code,
                    response_body=body,
                )
            elif response.status_code == 404:
                raise ResourceNotFoundError(
                    f"HTTP 404 Not Found: {error_msg}",
                    status_code=response.status_code,
                    response_body=body,
                )
            elif response.status_code == 400:
                if "quorum" in error_msg.lower() or "insufficient updates" in error_msg.lower():
                    raise QuorumNotMetError(
                        f"HTTP 400 Quorum Not Met: {error_msg}",
                        status_code=response.status_code,
                        response_body=body,
                    )
                raise ValidationError(
                    f"HTTP 400 Bad Request: {error_msg}",
                    status_code=response.status_code,
                    response_body=body,
                )
            else:
                raise FederatedNetworkError(
                    f"HTTP {response.status_code} Error: {error_msg}",
                    status_code=response.status_code,
                    response_body=body,
                )

        return body

    # -------------------------------------------------------------------------
    # Health & System Checks
    # -------------------------------------------------------------------------

    def check_health(self) -> bool:
        """
        Calls GET /actuator/health to verify Spring Boot control plane liveness.
        Returns True if status == 'UP'.
        """
        url = self._url("/actuator/health")
        try:
            res = self.session.get(url, timeout=self.timeout)
            if res.status_code == 200:
                data = res.json()
                return data.get("status") == "UP"
            return False
        except Exception as e:
            logger.warning(f"Control plane health check failed: {e}")
            return False

    # -------------------------------------------------------------------------
    # Node Management Endpoints
    # -------------------------------------------------------------------------

    def register_node(
        self,
        node_id: str,
        node_name: str,
        city_id: Optional[str] = None,
        endpoint_url: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Calls POST /api/v1/federated/nodes/register to register or update a municipal node.
        """
        url = self._url("/api/v1/federated/nodes/register")
        payload = {
            "nodeId": node_id.strip().upper(),
            "nodeName": node_name,
            "cityId": city_id,
            "endpointUrl": endpoint_url,
        }
        res = self.session.post(url, json=payload, timeout=self.timeout)
        return self._handle_response(res)

    def send_heartbeat(
        self,
        node_id: str,
        status: str = "ONLINE",
        model_version: str = "global-v1",
    ) -> Dict[str, Any]:
        """
        Calls POST /api/v1/federated/nodes/{nodeId}/heartbeat to update node status.
        """
        url = self._url(f"/api/v1/federated/nodes/{node_id.strip().upper()}/heartbeat")
        payload = {
            "status": status,
            "modelVersion": model_version,
        }
        res = self.session.post(url, json=payload, timeout=self.timeout)
        return self._handle_response(res)

    def get_node(self, node_id: str) -> Dict[str, Any]:
        """
        Calls GET /api/v1/federated/nodes/{nodeId}.
        """
        url = self._url(f"/api/v1/federated/nodes/{node_id.strip().upper()}")
        res = self.session.get(url, timeout=self.timeout)
        return self._handle_response(res)

    def list_nodes(self) -> List[Dict[str, Any]]:
        """
        Calls GET /api/v1/federated/nodes to retrieve all registered nodes.
        """
        url = self._url("/api/v1/federated/nodes")
        res = self.session.get(url, timeout=self.timeout)
        return self._handle_response(res)

    # -------------------------------------------------------------------------
    # Round Management Endpoints
    # -------------------------------------------------------------------------

    def create_round(
        self,
        round_id: Optional[str] = None,
        base_model_version: str = "global-v1",
        participating_nodes: Optional[List[str]] = None,
        min_quorum: int = 2,
    ) -> Dict[str, Any]:
        """
        Calls POST /api/v1/federated/rounds to initiate a new federated training round.
        """
        url = self._url("/api/v1/federated/rounds")
        payload: Dict[str, Any] = {
            "baseModelVersion": base_model_version,
            "minQuorum": min_quorum,
        }
        if round_id is not None:
            payload["roundId"] = round_id
        if participating_nodes is not None:
            payload["participatingNodes"] = [n.upper() for n in participating_nodes] if participating_nodes else []

        res = self.session.post(url, json=payload, timeout=self.timeout)
        return self._handle_response(res)

    def get_round(self, round_id: str) -> Dict[str, Any]:
        """
        Calls GET /api/v1/federated/rounds/{roundId} to query round status and updates.
        """
        url = self._url(f"/api/v1/federated/rounds/{round_id.strip().upper()}")
        res = self.session.get(url, timeout=self.timeout)
        return self._handle_response(res)

    def list_rounds(self) -> List[Dict[str, Any]]:
        """
        Calls GET /api/v1/federated/rounds to retrieve all historical rounds.
        """
        url = self._url("/api/v1/federated/rounds")
        res = self.session.get(url, timeout=self.timeout)
        return self._handle_response(res)

    # -------------------------------------------------------------------------
    # Update Submission & Aggregation Endpoints
    # -------------------------------------------------------------------------

    def submit_update(
        self,
        round_id: str,
        node_id: str,
        base_model_version: str,
        local_model_version: str,
        sample_count: int,
        metrics: Dict[str, float],
        weights: Optional[List[float]] = None,
        artifact_reference: Optional[str] = None,
        intercept: Optional[float] = None,
    ) -> Dict[str, Any]:
        """
        Calls POST /api/v1/federated/rounds/{roundId}/updates to transmit local model updates.
        Ensures canonical 37-weight dimension contract (36 features + intercept) is satisfied.
        """
        url = self._url(f"/api/v1/federated/rounds/{round_id.strip().upper()}/updates")
        w_list = [float(w) for w in weights] if weights is not None else []
        if len(w_list) == 36:
            w_list.append(float(intercept) if intercept is not None else 0.0)
        payload = {
            "nodeId": node_id.strip().upper(),
            "baseModelVersion": base_model_version,
            "localModelVersion": local_model_version,
            "sampleCount": sample_count,
            "metrics": metrics,
            "weights": w_list,
            "artifactReference": artifact_reference,
        }
        res = self.session.post(url, json=payload, timeout=self.timeout)
        return self._handle_response(res)

    def get_round_updates(self, round_id: str) -> List[Dict[str, Any]]:
        """
        Calls GET /api/v1/federated/rounds/{roundId}/updates.
        """
        url = self._url(f"/api/v1/federated/rounds/{round_id.strip().upper()}/updates")
        res = self.session.get(url, timeout=self.timeout)
        return self._handle_response(res)

    def trigger_aggregation(self, round_id: str) -> Dict[str, Any]:
        """
        Calls POST /api/v1/federated/rounds/{roundId}/aggregate to compute FedAvg consensus.
        """
        url = self._url(f"/api/v1/federated/rounds/{round_id.strip().upper()}/aggregate")
        res = self.session.post(url, timeout=self.timeout)
        return self._handle_response(res)

    # -------------------------------------------------------------------------
    # Global Consensus Model Queries
    # -------------------------------------------------------------------------

    def get_active_model(self) -> Dict[str, Any]:
        """
        Calls GET /api/v1/federated/models/active to query current consensus model.
        """
        url = self._url("/api/v1/federated/models/active")
        res = self.session.get(url, timeout=self.timeout)
        return self._handle_response(res)

    def get_model_catalog(self) -> List[Dict[str, Any]]:
        """
        Calls GET /api/v1/federated/models/catalog to retrieve model version history.
        """
        url = self._url("/api/v1/federated/models/catalog")
        res = self.session.get(url, timeout=self.timeout)
        return self._handle_response(res)

    def get_model(self, model_version: str) -> Dict[str, Any]:
        """
        Calls GET /api/v1/federated/models/{modelVersion}.
        """
        url = self._url(f"/api/v1/federated/models/{model_version.strip()}")
        res = self.session.get(url, timeout=self.timeout)
        return self._handle_response(res)
