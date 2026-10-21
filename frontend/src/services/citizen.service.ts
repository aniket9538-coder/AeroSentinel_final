import apiClient from './api';
import { CitizenReport } from '../types';

export const citizenService = {
  getReports: async (cityId: string): Promise<CitizenReport[]> => {
    const response = await apiClient.get<CitizenReport[]>(`/citizen/reports?cityId=${cityId}`);
    return response.data;
  },
  getReportById: async (reportId: string): Promise<CitizenReport> => {
    const response = await apiClient.get<CitizenReport>(`/citizen/reports/${reportId}`);
    return response.data;
  },
  getReportByReference: async (reference: string): Promise<CitizenReport> => {
    const response = await apiClient.get<CitizenReport>(`/citizen/reports/reference/${encodeURIComponent(reference.trim())}`);
    return response.data;
  },
  getMyReports: async (): Promise<CitizenReport[]> => {
    const response = await apiClient.get<CitizenReport[]>('/citizen/reports/my');
    return response.data;
  },
  submitReport: async (formData: FormData): Promise<CitizenReport> => {
    const response = await apiClient.post<CitizenReport>('/citizen/reports', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      timeout: 35000,
    });
    return response.data;
  },
};
