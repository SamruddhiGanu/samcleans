// Browser File System Access API scanner
// Enumerates all files in a chosen directory recursively, collecting only
// metadata (name, size, lastModified, type). No file content is read or uploaded.
// Works in Chrome 86+ and Edge 86+.

import type { BrowserFileMetadata } from './types';

export interface ScanProgress {
  total: number;
  scanned: number;
  currentFile: string;
}

export async function pickAndScanFolder(
  onProgress?: (p: ScanProgress) => void
): Promise<{ rootPath: string; files: BrowserFileMetadata[] }> {
  // @ts-ignore — File System Access API types
  const dirHandle = await window.showDirectoryPicker({ mode: 'read' });
  const rootPath: string = dirHandle.name;
  const files: BrowserFileMetadata[] = [];

  await collectFiles(dirHandle, '', files, onProgress, { total: 0, scanned: 0, currentFile: '' });

  return { rootPath, files };
}

async function collectFiles(
  dirHandle: FileSystemDirectoryHandle,
  relativePath: string,
  out: BrowserFileMetadata[],
  onProgress: ((p: ScanProgress) => void) | undefined,
  progress: ScanProgress
): Promise<void> {
  // @ts-ignore
  for await (const [name, handle] of dirHandle.entries()) {
    // Skip hidden / system directories
    if (name.startsWith('.') || EXCLUDED.has(name)) continue;

    const entryPath = relativePath ? `${relativePath}/${name}` : name;

    if (handle.kind === 'directory') {
      await collectFiles(handle, entryPath, out, onProgress, progress);
    } else {
      try {
        const file: File = await handle.getFile();
        out.push({
          path: entryPath,
          name,
          sizeBytes: file.size,
          lastModifiedMs: file.lastModified,
          mimeType: file.type ?? '',
        });
        progress.scanned++;
        progress.currentFile = name;
        if (onProgress) onProgress({ ...progress, total: out.length });
      } catch {
        // Skip files that can't be read (permissions, etc.)
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
