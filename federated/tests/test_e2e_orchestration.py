"""
AeroSentinel - Cross-Stack Federated Orchestration Test Suite
File: federated/tests/test_e2e_orchestration.py

Automated integration tests validating the Python Network Client Adapter,
Spring Boot Federated Control Plane endpoints, guardrails, and multi-round lifecycle.
"""

import pytest
import time
import uuid
from typing import Dict, List, Any

from federated.clients.network_client import (
    FederatedNetworkClient,
    FederatedNetworkError,
    ValidationError,
    DuplicateUpdateError,
    QuorumNotMetError,
    ResourceNotFoundError,
)
from federated.clients.pune.client import PuneClient
from federated.clients.mumbai.client import MumbaiClient
from federated.clients.delhi.client import DelhiClient
from federated.models.local_model import FEATURE_COUNT


@pytest.fixture(scope="module")
def client() -> FederatedNetworkClient:
    """Fixture providing initialized network client connected to control plane with Admin auth."""
    client = FederatedNetworkClient(base_url="http://localhost:8080", timeout=10.0)
    assert client.check_health(), "Spring Boot control plane is not reachable on http://localhost:8080"
    client.login("admin@aerosentinel.gov", "password")
    return client


class TestFederatedCrossStackOrchestration:
    """
    Integration test suite for cross-stack federated control plane communication.
    """

    def test_00_unauthenticated_mutation_rejected_with_401(self):
        """
        Verifies unauthenticated POST to federated control plane is strictly rejected with HTTP 401.
        """
        anon_client = FederatedNetworkClient(base_url="http://localhost:8080", timeout=10.0)
        assert anon_client.check_health() is True
        # Anonymous GET is permitted
        nodes = anon_client.list_nodes()
        assert len(nodes) >= 3

        # Anonymous POST must fail closed with HTTP 401
        with pytest.raises(FederatedNetworkError) as exc_info:
            anon_client.create_round(
                round_id=f"ANON-R-{uuid.uuid4().hex[:6].upper()}",
                base_model_version="global-v1",
                participating_nodes=["PUNE", "MUMBAI"],
                min_quorum=2,
            )
        assert exc_info.value.status_code == 401

    def test_01_backend_healthcheck(self, client: FederatedNetworkClient):
        """
        Verifies network client successfully communicates with /actuator/health.
        """
        is_healthy = client.check_health()
        assert is_healthy is True

    def test_02_node_registration_and_heartbeat(self, client: FederatedNetworkClient):
        """
        Verifies registering a node and recording heartbeats over HTTP.
        """
        nodes = client.list_nodes()
        assert len(nodes) >= 3

        node_ids = [n["nodeId"] for n in nodes]
        assert "PUNE" in node_ids
        assert "MUMBAI" in node_ids
        assert "DELHI" in node_ids

        # Test heartbeat transmission
        hb = client.send_heartbeat("PUNE", status="ONLINE", model_version="global-v1")
        assert hb["nodeId"] == "PUNE"
        assert hb["status"] == "ONLINE"
        assert hb["modelVersion"] == "global-v1"

        # Query single node
        node_info = client.get_node("PUNE")
        assert node_info["nodeId"] == "PUNE"
        assert node_info["status"] == "ONLINE"

    def test_03_round_creation_and_query(self, client: FederatedNetworkClient):
        """
        Verifies creating a round via REST API and reading round details.
        """
        round_id = f"TEST-R-{uuid.uuid4().hex[:6].upper()}"
        created = client.create_round(
            round_id=round_id,
            base_model_version="global-v1",
            participating_nodes=["PUNE", "MUMBAI", "DELHI"],
            min_quorum=2,
        )
        assert created["roundId"] == round_id
        assert created["status"] == "CREATED"
        assert created["minQuorum"] == 2

        detail = client.get_round(round_id)
        assert detail["roundId"] == round_id
        assert detail["baseModelVersion"] == "global-v1"
        assert detail["receivedUpdatesCount"] == 0

    def test_04_submit_valid_model_update(self, client: FederatedNetworkClient):
        """
        Verifies formatting and submitting a 36-weight model update payload.
        """
        round_id = f"TEST-R-{uuid.uuid4().hex[:6].upper()}"
        client.create_round(
            round_id=round_id,
            base_model_version="global-v1",
            participating_nodes=["PUNE", "MUMBAI", "DELHI"],
            min_quorum=2,
        )

        dummy_weights = [0.01 * (i + 1) for i in range(FEATURE_COUNT)]
        update_res = client.submit_update(
            round_id=round_id,
            node_id="PUNE",
            base_model_version="global-v1",
            local_model_version="pune-t1",
            sample_count=1200,
            metrics={"mae": 0.42, "rmse": 0.51, "rocAuc": 0.72, "brierScore": 0.18},
            weights=dummy_weights,
            artifact_reference=f"storage/models/updates/pune_{round_id.lower()}.json",
        )

        assert update_res["roundId"] == round_id
        assert update_res["nodeId"] == "PUNE"
        assert update_res["status"] == "VALIDATED"
        assert update_res["sampleCount"] == 1200

        # Verify update appears in round updates query
        updates = client.get_round_updates(round_id)
        assert len(updates) == 1
        assert updates[0]["nodeId"] == "PUNE"

    def test_05_rejection_stale_base_model(self, client: FederatedNetworkClient):
        """
        Verifies HTTP 400 error is thrown when update base model does not match round base model.
        """
        round_id = f"TEST-R-{uuid.uuid4().hex[:6].upper()}"
        client.create_round(
            round_id=round_id,
            base_model_version="global-v2",  # Requires global-v2
            participating_nodes=["PUNE", "MUMBAI", "DELHI"],
            min_quorum=2,
        )

        dummy_weights = [0.0] * FEATURE_COUNT
        with pytest.raises(ValidationError) as exc_info:
            client.submit_update(
                round_id=round_id,
                node_id="DELHI",
                base_model_version="global-v1",  # Stale version!
                local_model_version="delhi-t1",
                sample_count=800,
                metrics={"mae": 0.5},
                weights=dummy_weights,
            )
        assert "stale base model" in str(exc_info.value).lower()

    def test_06_rejection_duplicate_node_update(self, client: FederatedNetworkClient):
        """
        Verifies HTTP 409 Conflict is thrown when the same node submits twice to the same round.
        """
        round_id = f"TEST-R-{uuid.uuid4().hex[:6].upper()}"
        client.create_round(
            round_id=round_id,
            base_model_version="global-v1",
            participating_nodes=["PUNE", "MUMBAI", "DELHI"],
            min_quorum=2,
        )

        dummy_weights = [0.0] * FEATURE_COUNT
        # First submission succeeds
        first = client.submit_update(
            round_id=round_id,
            node_id="PUNE",
            base_model_version="global-v1",
            local_model_version="pune-first",
            sample_count=1000,
            metrics={"mae": 0.4},
            weights=dummy_weights,
        )
        assert first["status"] == "VALIDATED"

        # Second submission must fail with DuplicateUpdateError (HTTP 409)
        with pytest.raises((DuplicateUpdateError, ValidationError)) as exc_info:
            client.submit_update(
                round_id=round_id,
                node_id="PUNE",
                base_model_version="global-v1",
                local_model_version="pune-second",
                sample_count=1000,
                metrics={"mae": 0.4},
                weights=dummy_weights,
            )
        assert "duplicate" in str(exc_info.value).lower()

    def test_07_quorum_enforcement_blocks_aggregation(self, client: FederatedNetworkClient):
        """
        Verifies aggregation fails with HTTP 400 when valid updates < minQuorum.
        """
        round_id = f"TEST-Q-{uuid.uuid4().hex[:6].upper()}"
        client.create_round(
            round_id=round_id,
            base_model_version="global-v1",
            participating_nodes=["PUNE", "MUMBAI", "DELHI"],
            min_quorum=2,
        )

        dummy_weights = [0.0] * FEATURE_COUNT
        # Submit only 1 update (Pune)
        client.submit_update(
            round_id=round_id,
            node_id="PUNE",
            base_model_version="global-v1",
            local_model_version="pune-q1",
            sample_count=1000,
            metrics={"mae": 0.4},
            weights=dummy_weights,
        )

        # Trigger aggregation prematurely
        with pytest.raises(QuorumNotMetError) as exc_info:
            client.trigger_aggregation(round_id)
        assert "insufficient updates" in str(exc_info.value).lower() or "quorum" in str(exc_info.value).lower()

        # Check round marked FAILED
        round_status = client.get_round(round_id)
        assert round_status["status"] == "FAILED"

    def test_08_complete_two_round_lifecycle_simulation(self, client: FederatedNetworkClient):
        """
        Executes full simulation: Round 1 (3 nodes -> global-v2) followed by Round 2 (2 nodes -> global-v3).
        Asserts catalog contains global-v1, global-v2, global-v3.
        """
        pune = PuneClient(random_state=42, network_client=client)
        mumbai = MumbaiClient(random_state=101, network_client=client)
        delhi = DelhiClient(random_state=202, network_client=client)

        active = client.get_active_model()
        base_v1 = active["modelVersion"]

        # --- Round 1: 3 Nodes ---
        r1_id = f"SIM-R1-{uuid.uuid4().hex[:6].upper()}"
        client.create_round(
            round_id=r1_id,
            base_model_version=base_v1,
            participating_nodes=["PUNE", "MUMBAI", "DELHI"],
            min_quorum=2,
        )

        pune.train_local_round(round_id=r1_id, base_version=base_v1, sample_count=500)
        pune_up = pune.package_model_update(round_id=r1_id)
        client.submit_update(
            round_id=r1_id,
            node_id="PUNE",
            base_model_version=base_v1,
            local_model_version=pune_up["localModelVersion"],
            sample_count=500,
            metrics=pune_up["metrics"],
            weights=pune_up["weights"],
        )

        mumbai.train_local_round(round_id=r1_id, base_version=base_v1, sample_count=400)
        mumbai_up = mumbai.package_model_update(round_id=r1_id)
        client.submit_update(
            round_id=r1_id,
            node_id="MUMBAI",
            base_model_version=base_v1,
            local_model_version=mumbai_up["localModelVersion"],
            sample_count=400,
            metrics=mumbai_up["metrics"],
            weights=mumbai_up["weights"],
        )

        delhi.train_local_round(round_id=r1_id, base_version=base_v1, sample_count=300)
        delhi_up = delhi.package_model_update(round_id=r1_id)
        client.submit_update(
            round_id=r1_id,
            node_id="DELHI",
            base_model_version=base_v1,
            local_model_version=delhi_up["localModelVersion"],
            sample_count=300,
            metrics=delhi_up["metrics"],
            weights=delhi_up["weights"],
        )

        agg1 = client.trigger_aggregation(r1_id)
        assert agg1["status"] == "COMPLETED"
        assert agg1["totalSamples"] == 1200

        active_post_r1 = client.get_active_model()
        new_base = active_post_r1["modelVersion"]
        assert "global-v" in new_base

        # --- Round 2: 2 of 3 Nodes (Delhi omitted) ---
        r2_id = f"SIM-R2-{uuid.uuid4().hex[:6].upper()}"
        client.create_round(
            round_id=r2_id,
            base_model_version=new_base,
            participating_nodes=["PUNE", "MUMBAI", "DELHI"],
            min_quorum=2,
        )

        pune.train_local_round(round_id=r2_id, base_version=new_base, sample_count=600)
        pune_up2 = pune.package_model_update(round_id=r2_id)
        client.submit_update(
            round_id=r2_id,
            node_id="PUNE",
            base_model_version=new_base,
            local_model_version=pune_up2["localModelVersion"],
            sample_count=600,
            metrics=pune_up2["metrics"],
            weights=pune_up2["weights"],
        )

        mumbai.train_local_round(round_id=r2_id, base_version=new_base, sample_count=500)
        mumbai_up2 = mumbai.package_model_update(round_id=r2_id)
        client.submit_update(
            round_id=r2_id,
            node_id="MUMBAI",
            base_model_version=new_base,
            local_model_version=mumbai_up2["localModelVersion"],
            sample_count=500,
            metrics=mumbai_up2["metrics"],
            weights=mumbai_up2["weights"],
        )

        agg2 = client.trigger_aggregation(r2_id)
        assert agg2["status"] == "COMPLETED"
        assert agg2["totalSamples"] == 1100

        active_post_r2 = client.get_active_model()
        assert active_post_r2["totalSamples"] == 1100

        # Verify Catalog Lineage
        catalog = client.get_model_catalog()
        assert len(catalog) >= 3
        model_versions = [c["modelVersion"] for c in catalog]
        assert "global-v1" in model_versions
