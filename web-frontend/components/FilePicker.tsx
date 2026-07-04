"use client";

import { ChangeEvent, useRef, useState } from "react";
import { FolderSearch, Play, Loader2, CheckCircle2 } from "lucide-react";
import { pickAndScanFolder, scanFileList, formatBytes, isFileSystemAccessSupported, type ScanProgress } from "@/lib/filescanner";
import { submitBrowserMetadata } from "@/lib/api";
import type { ScanSession } from "@/lib/types";
import { cn } from "@/lib/utils";

interface FilePickerProps {
  onScanComplete: (session: ScanSession) => void;
  hasActiveSession?: boolean;
}

export function FilePicker({ onScanComplete, hasActiveSession = false }: FilePickerProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isScanningLocal, setIsScanningLocal] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [progress, setProgress] = useState<ScanProgress | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [lastScanSummary, setLastScanSummary] = useState<{ files: number; size: number } | null>(null);

  const submitScan = async (scan: Promise<{ rootPath: string; files: Parameters<typeof submitBrowserMetadata>[0]["files"] }>) => {
    try {
      setError(null);
      setLastScanSummary(null);
      setIsScanningLocal(true);

      const { rootPath, files } = await scan;

      setIsScanningLocal(false);
      setIsSubmitting(true);

      // Submit metadata to backend (synchronous DB persist + async analysis)
      const session = await submitBrowserMetadata({
        sessionName: `Web Scan: ${rootPath}`,
        rootPath,
        files,
      });

      const totalSize = files.reduce((acc, f) => acc + f.sizeBytes, 0);
      setLastScanSummary({ files: files.length, size: totalSize });
      setIsSubmitting(false);
      setProgress(null);
      onScanComplete(session);

    } catch (err: unknown) {
      if (!(err instanceof DOMException && err.name === "AbortError")) {
        setError(err instanceof Error ? err.message : "Failed to scan folder");
      }
      setIsScanningLocal(false);
      setIsSubmitting(false);
      setProgress(null);
    }
  };

  const handleScan = async () => {
    if (!isFileSystemAccessSupported()) {
      fileInputRef.current?.click();
      return;
    }

    await submitScan(pickAndScanFolder((p) => setProgress({ ...p })));
  };

  const handleFallbackFolderSelected = async (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = event.target.files;
    event.target.value = "";
    if (!selectedFiles || selectedFiles.length === 0) {
      return;
    }

    await submitScan(scanFileList(selectedFiles, (p) => setProgress({ ...p })));
  };

  const isScanning = isScanningLocal || isSubmitting;
  const buttonLabel = isScanning
    ? isScanningLocal ? "Reading Files…" : "Analyzing…"
    : hasActiveSession ? "Scan New Folder" : "Select Folder";

  return (
    <div className="bg-card border border-border rounded-xl p-6 shadow-sm card-hover-effect">
      <div className="flex items-start justify-between">
        <input
          ref={fileInputRef}
          type="file"
          multiple
          className="hidden"
          onChange={handleFallbackFolderSelected}
          // @ts-expect-error webkitdirectory is supported by Chromium browsers but not typed by React.
          webkitdirectory=""
        />
        <div>
          <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
            <FolderSearch className="text-primary" size={20} />
            Scan Storage
          </h2>
          <p className="text-sm text-muted-foreground mt-1">
            Select a local folder to analyze. No files are uploaded — only metadata is sent securely.
          </p>
        </div>

        <button
          onClick={handleScan}
          disabled={isScanning}
          className={cn(
            "flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-all premium-glow",
            isScanning
              ? "bg-secondary text-muted-foreground cursor-not-allowed"
              : "bg-primary text-primary-foreground hover:bg-primary/90"
          )}
        >
          {isScanning ? (
            <Loader2 size={18} className="animate-spin" />
          ) : (
            <Play size={18} fill="currentColor" />
          )}
          {buttonLabel}
        </button>
      </div>

      {/* Progress bar while reading local files */}
      {progress && isScanningLocal && (
        <div className="mt-6 space-y-2">
          <div className="flex justify-between text-xs text-muted-foreground">
            <span>{progress.status || "Scanning locally…"}</span>
            <span>{progress.scanned.toLocaleString()} files</span>
          </div>
          <div className="h-2 bg-secondary rounded-full overflow-hidden">
            <div
              className="h-full bg-primary rounded-full transition-all duration-300"
              style={{ width: `${Math.min(100, (progress.scanned / (progress.total || 1)) * 100)}%` }}
            />
          </div>
          <p className="text-xs text-muted-foreground truncate opacity-70">
            {progress.currentFile}
          </p>
        </div>
      )}

      {/* Uploading/analyzing state */}
      {isSubmitting && (
        <div className="mt-6 flex items-center gap-3 text-sm text-muted-foreground">
          <Loader2 size={16} className="animate-spin text-primary" />
          <span>Submitting metadata and running analysis…</span>
        </div>
      )}

      {/* Post-scan success summary */}
      {lastScanSummary && !isScanning && (
        <div className="mt-4 flex items-center gap-2 text-sm text-emerald-400">
          <CheckCircle2 size={16} />
          <span>
            Scan complete — {lastScanSummary.files.toLocaleString()} files ({formatBytes(lastScanSummary.size)}) analyzed
          </span>
        </div>
      )}

      {error && (
        <div className="mt-4 p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-sm">
          {error}
        </div>
      )}
    </div>
  );
}
