import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

export interface User {
  email: string;
  full_name: string;
  resume_text?: string;
  skills?: string[];
  preferences?: Record<string, any>;
  created_at: string;
  updated_at: string;
}

export interface JobMatch {
  job_id: number;
  job_link_id: number;
  title: string;
  company: string;
  location?: string;
  salary_range?: string;
  job_type?: string;
  match_score: number;
  match_reason: string;
  matched_skills: string[];
  url: string;
}

export interface Application {
  id: number;
  user_email: string;
  job_info_id: number;
  cover_letter: string;
  application_status: string;
  applied_at: string;
}

// User API
export const createUser = async (email: string, fullName: string, password: string): Promise<User> => {
  const response = await api.post('/users/register', { email, fullName, password });
  return response.data;
};

export const getUser = async (email: string): Promise<User> => {
  const response = await api.get(`/users/${email}`);
  return response.data;
};

export const updateUserResume = async (email: string, resumeText: string): Promise<User> => {
  const response = await api.post(`/users/${email}/resume`, { resumeText });
  return response.data;
};

// Job Matching API
export const getJobMatches = async (email: string, limit: number = 50): Promise<JobMatch[]> => {
  const response = await api.get(`/users/${email}/matches`, { params: { limit } });
  return response.data.matches || [];
};

export const refreshJobMatches = async (email: string): Promise<{ message: string; matches_found: number }> => {
  const response = await api.post(`/users/${email}/matches/refresh`);
  return response.data;
};

// Application API
export const getUserApplications = async (email: string): Promise<Application[]> => {
  const response = await api.get(`/users/${email}/applications`);
  return response.data;
};

export const createApplication = async (
  email: string,
  jobInfoId: number
): Promise<{ application_id: number; cover_letter: string }> => {
  const response = await api.post(`/users/${email}/applications`, { job_info_id: jobInfoId });
  return response.data;
};

// Health Check
export const healthCheck = async (): Promise<{ status: string; timestamp: string }> => {
  const response = await api.get('/health');
  return response.data;
};

export default api;
