// API client — mirrors ApiClientService.java but as a browser fetch wrapper
import type {
  ScanSession, HealthScore, DuplicateAnalysis,
  Recommendation, RankedFile, CleanupSession, BrowserScanRequest,
  FileTypeBreakdown
} from './types';
const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://127.0.0.1:8080';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`API ${res.status}: ${text}`);
  }
  // 204 No Content
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

// ── Scan ──────────────────────────────────────────────────────────────────────

export async function startScan(path: string, name: string): Promise<ScanSession> {
  return request('/api/scan/start', {
    method: 'POST',
    body: JSON.stringify({ path, name }),
  });
}

export async function submitBrowserMetadata(data: BrowserScanRequest): Promise<ScanSession> {
  return request('/api/scan/submit-metadata', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function getScanSession(id: number): Promise<ScanSession> {
  return request(`/api/scan/${id}`);
}

export async function listScanSessions(page = 0, size = 20): Promise<{ content: ScanSession[]; totalElements: number }> {
  return request(`/api/scan/list?page=${page}&size=${size}`);
}

export async function cancelScan(id: number): Promise<void> {
  return request(`/api/scan/cancel/${id}`, { method: 'POST' });
}

// ── Health ────────────────────────────────────────────────────────────────────

export async function getHealthScore(sessionId: number): Promise<HealthScore> {
  return request(`/api/health/score/${sessionId}`);
}

export async function getBreakdown(sessionId: number): Promise<FileTypeBreakdown> {
  return request(`/api/health/breakdown/${sessionId}`);
}

// ── Duplicates ────────────────────────────────────────────────────────────────

export async function detectDuplicates(sessionId: number): Promise<DuplicateAnalysis> {
  return request(`/api/duplicates/detect/${sessionId}`, { method: 'POST' });
}

export async function getDuplicateRecommendations(page = 0, size = 50): Promise<{ content: Recommendation[] }> {
  return request(`/api/duplicates/recommendations?page=${page}&size=${size}`);
}

// ── Recommendations ───────────────────────────────────────────────────────────

export async function generateRecommendations(sessionId: number): Promise<void> {
  return request(`/api/recommendations/generate/${sessionId}`, { method: 'POST' });
}

export async function getRecommendations(
  sessionId: number,
  type?: string,
  page = 0,
  size = 100
): Promise<{ content: Recommendation[] }> {
  const typeParam = type && type !== 'ALL' ? `&type=${type}` : '';
  return request(`/api/recommendations/list?page=${page}&size=${size}${typeParam}`);
}

export async function markRecommendationActedOn(id: number): Promise<void> {
  return request(`/api/recommendations/${id}/acted`, { method: 'PATCH' });
}

// ── Ranking ───────────────────────────────────────────────────────────────────

export async function runRanking(sessionId: number): Promise<void> {
  return request(`/api/ranking/run/${sessionId}`, { method: 'POST' });
}

export async function getRankedFiles(sessionId: number): Promise<RankedFile[]> {
  return request(`/api/ranking/files/${sessionId}`);
}

// ── Cleanup ───────────────────────────────────────────────────────────────────

export async function initiateCleanup(fileIds: number[]): Promise<{ sessionId: string }> {
  return request('/api/cleanup/initiate', {
    method: 'POST',
    body: JSON.stringify({ fileIds }),
  });
}

export async function executeCleanup(sessionId: string): Promise<void> {
  return request(`/api/cleanup/execute/${sessionId}`, { method: 'POST' });
}

export async function undoCleanup(sessionId: string): Promise<void> {
  return request(`/api/cleanup/undo/${sessionId}`, { method: 'POST' });
}

export async function listCleanupSessions(): Promise<CleanupSession[]> {
  return request('/api/cleanup/sessions');
}
