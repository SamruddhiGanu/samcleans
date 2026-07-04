// Browser File System Access API scanner
// Enumerates all files in a chosen directory recursively.
// Also computes SHA-256 hashes for files with identical sizes to detect duplicates.

import type { BrowserFileMetadata } from './types';

export interface ScanProgress {
  total: number;
  scanned: number;
  currentFile: string;
  status?: string;
}

interface CollectedFile {
  meta: BrowserFileMetadata;
  file: File;
}

const LARGE_FILE_SAMPLE_BYTES = 1024 * 1024;
const MAX_FULL_HASH_BYTES = 512 * 1024 * 1024;

export async function pickAndScanFolder(
  onProgress?: (p: ScanProgress) => void
): Promise<{ rootPath: string; files: BrowserFileMetadata[] }> {
  // @ts-ignore — File System Access API types
  const dirHandle = await window.showDirectoryPicker({ mode: 'read' });
  const rootPath: string = dirHandle.name;
  
  const collected: CollectedFile[] = [];
  const progress: ScanProgress = { total: 0, scanned: 0, currentFile: '', status: 'Scanning directories...' };

  await collectFiles(dirHandle, '', collected, onProgress, progress);

  // Post-process: Find files with identical sizes to hash them for duplicate detection
  if (onProgress) {
    onProgress({ ...progress, status: 'Computing hashes for potential duplicates...' });
  }

  const sizeGroups = new Map<number, CollectedFile[]>();
  for (const item of collected) {
    if (!sizeGroups.has(item.meta.sizeBytes)) {
      sizeGroups.set(item.meta.sizeBytes, []);
    }
    sizeGroups.get(item.meta.sizeBytes)!.push(item);
  }

  let hashCount = 0;
  for (const [size, group] of sizeGroups.entries()) {
    if (group.length > 1) {
      for (const item of group) {
        try {
          if (onProgress) {
            onProgress({ ...progress, status: `Hashing ${item.meta.name}...` });
          }
          item.meta.sha256Hash = await hashPotentialDuplicate(item.file);
          hashCount++;
        } catch (e) {
          console.warn("Could not hash file in browser (too large?):", item.meta.name, e);
        }
      }
    }
  }

  console.log(`Computed SHA-256 hashes for ${hashCount} potential duplicate files.`);

  // Return just the metadata array
  const files = collected.map(c => c.meta);
  return { rootPath, files };
}

async function hashPotentialDuplicate(file: File): Promise<string> {
  if (file.size === 0) {
    return "empty-file";
  }

  if (file.size > MAX_FULL_HASH_BYTES) {
    return sampledFileHash(file);
  }

  try {
    return await sha256Hex(await file.arrayBuffer());
  } catch (error) {
    console.warn("Full-file hash failed; falling back to sampled hash:", file.name, error);
    return sampledFileHash(file);
  }
}

async function sampledFileHash(file: File): Promise<string> {
  const first = file.slice(0, LARGE_FILE_SAMPLE_BYTES);
  const lastStart = Math.max(LARGE_FILE_SAMPLE_BYTES, file.size - LARGE_FILE_SAMPLE_BYTES);
  const last = file.slice(lastStart, file.size);
  const combined = await new Blob([first, last]).arrayBuffer();
  return `sampled:${file.size}:${await sha256Hex(combined)}`;
}

async function sha256Hex(buffer: ArrayBuffer): Promise<string> {
  const hashBuffer = await crypto.subtle.digest('SHA-256', buffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

async function collectFiles(
  dirHandle: FileSystemDirectoryHandle,
  relativePath: string,
  out: CollectedFile[],
  onProgress: ((p: ScanProgress) => void) | undefined,
  progress: ScanProgress
): Promise<void> {
  // @ts-ignore
  for await (const [name, handle] of dirHandle.entries()) {
    if (name.startsWith('.') || EXCLUDED.has(name)) continue;

    const entryPath = relativePath ? `${relativePath}/${name}` : name;

    if (handle.kind === 'directory') {
      await collectFiles(handle, entryPath, out, onProgress, progress);
    } else {
      try {
        const file: File = await handle.getFile();
        out.push({
          meta: {
            path: entryPath,
            name,
            sizeBytes: file.size,
            lastModifiedMs: file.lastModified,
            mimeType: file.type ?? '',
          },
          file
        });
        progress.scanned++;
        progress.currentFile = name;
        if (onProgress) onProgress({ ...progress, total: out.length });
      } catch {
        // Skip files that can't be read
      }
    }
  }
}

const EXCLUDED = new Set([
  'node_modules', '.git', '__pycache__', '$RECYCLE.BIN',
  'System Volume Information', 'Thumbs.db', '.DS_Store',
]);

export function isFileSystemAccessSupported(): boolean {
  return typeof window !== 'undefined' && 'showDirectoryPicker' in window;
}

export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}
