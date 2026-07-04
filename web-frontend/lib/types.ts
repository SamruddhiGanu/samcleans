// Shared TypeScript types matching the Spring Boot DTOs

export interface FileTypeBreakdown {
  sizeByType: Record<string, number>; // e.g. { IMAGE: 1048576, VIDEO: 524288 }
  totalSize: number;
}

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
  groupCount: number;          // renamed from totalGroups — matches backend field
  totalDuplicateFiles: number; // sum of all fileCount in groups
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
  sha256Hash?: string;
}

export interface BrowserScanRequest {
  sessionName: string;
  rootPath: string;
  files: BrowserFileMetadata[];
}
