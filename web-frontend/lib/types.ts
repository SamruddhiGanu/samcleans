// Shared TypeScript types matching the Spring Boot DTOs

export interface ScanSession {
  id: number;
  sessionName: string;
  scanPath: string;
  status: 'INITIATED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'PAUSED';
  totalFiles: number | null;
  totalSize: number | null;
  startTime: string | null;
  endTime: string | null;
}

export interface HealthScore {
  overallScore: number;
  duplicateWasteScore: number;
  clutterScore: number;
  organizationScore: number;
  healthStatus: 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR';
}

export interface DuplicateGroup {
  hashValue: string;
  fileCount: number;
  totalSize: number;
  recoverableSpace: number;
  filePaths: string[];
}

export interface DuplicateAnalysis {
  sessionId: number;
  totalGroups: number;
  totalDuplicateFiles: number;
  totalRecoverableSpace: number;
  groups: DuplicateGroup[];
}

export interface Recommendation {
  id: number;
  fileId: number;
  fileName: string;
  filePath: string;
  type: string;
  confidenceScore: number;
  explanation: string;
  recoverableSpace: number | null;
}

export interface RankedFile {
  id: number;
  name: string;
  path: string;
  sizeBytes: number;
  fileType: string;
  importanceScore: number;
}

export interface CleanupSession {
  sessionId: string;
  filesCount: number;
  totalSize: number;
  status: string;
}

export interface BrowserFileMetadata {
  path: string;
  name: string;
  sizeBytes: number;
  lastModifiedMs: number;
  mimeType: string;
}

export interface BrowserScanRequest {
  sessionName: string;
  rootPath: string;
  files: BrowserFileMetadata[];
}
