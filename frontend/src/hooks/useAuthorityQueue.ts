import { useState, useEffect, useCallback } from 'react';
import { AuthorityQueueItem } from '../types/alert';
import alertApi from '../services/alertApi';

interface UseAuthorityQueueOptions {
  cityId?: string;
  status?: string;
}

export function useAuthorityQueue(options: UseAuthorityQueueOptions = {}) {
  const [items, setItems] = useState<AuthorityQueueItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [isUnauthorized, setIsUnauthorized] = useState<boolean>(false);
  const [selectedAlertId, setSelectedAlertId] = useState<string | null>(null);

  const fetchQueue = useCallback(async () => {
    setLoading(true);
    setError(null);
    setIsUnauthorized(false);
    try {
      const data = await alertApi.getAuthorityQueue({
        cityId: options.cityId,
        status: options.status,
      });
      setItems(data);
      if (data.length > 0 && !selectedAlertId) {
        setSelectedAlertId(data[0].alertId);
      }
    } catch (err: any) {
      const isAuthErr =
        err?.response?.status === 401 ||
        err?.response?.data?.status === 401 ||
        (err?.response?.data?.message || '').toLowerCase().includes('authentication required');
      setIsUnauthorized(isAuthErr);
      setError(err?.response?.data?.message || err?.message || 'Unable to load authority alerts.');
    } finally {
      setLoading(false);
    }
  }, [options.cityId, options.status]);

  useEffect(() => {
    fetchQueue();
  }, [fetchQueue]);

  const acknowledge = async (alertId: string, userId?: string) => {
    const updated = await alertApi.acknowledgeAlert(alertId, userId);
    setItems((prev) =>
      prev.map((item) => (item.alertId === alertId ? updated : item))
    );
    return updated;
  };

  const resolve = async (alertId: string, notes?: string, userId?: string) => {
    const updated = await alertApi.resolveAlert(alertId, notes, userId);
    setItems((prev) =>
      prev.map((item) => (item.alertId === alertId ? updated : item))
    );
    return updated;
  };

  const dismiss = async (alertId: string, reason: string, userId?: string) => {
    const updated = await alertApi.dismissAlert(alertId, reason, userId);
    setItems((prev) =>
      prev.map((item) => (item.alertId === alertId ? updated : item))
    );
    return updated;
  };

  const selectedAlert = items.find((item) => item.alertId === selectedAlertId) || null;

  return {
    items,
    loading,
    error,
    isUnauthorized,
    selectedAlert,
    selectedAlertId,
    setSelectedAlertId,
    refresh: fetchQueue,
    acknowledge,
    resolve,
    dismiss,
  };
}

export default useAuthorityQueue;
