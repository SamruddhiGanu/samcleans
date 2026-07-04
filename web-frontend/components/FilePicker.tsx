"use client";

import { useState } from "react";
import { FolderSearch, Play, Loader2 } from "lucide-react";
import { pickAndScanFolder, formatBytes, isFileSystemAccessSupported, type ScanProgress } from "@/lib/filescanner";
import { submitBrowserMetadata } from "@/lib/api";
import type { ScanSession } from "@/lib/types";
import { cn } from "@/lib/utils";

interface FilePickerProps {
  onScanComplete: (session: ScanSession) => void;
}

export function FilePicker({ onScanComplete }: FilePickerProps) {
  const [isScanningLocal, setIsScanningLocal] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [progress, setProgress] = useState<ScanProgress | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleScan = async () => {
    if (!isFileSystemAccessSupported()) {
      setError("Your browser does not support the File System Access API. Please use Chrome or Edge.");
      return;
    }

    try {
      setError(null);
      setIsScanningLocal(true);
      
      const { rootPath, files } = await pickAndScanFolder((p) => setProgress({ ...p }));
      
      setIsScanningLocal(false);
      setIsSubmitting(true);
      
      // Submit metadata to backend
      const session = await submitBrowserMetadata({
        sessionName: `Web Scan: ${rootPath}`,
        rootPath,
        files,
      });
      
      setIsSubmitting(false);
      setProgress(null);
      onScanComplete(session);
      
    } catch (err: any) {
      if (err.name !== "AbortError") {
        setError(err.message || "Failed to scan folder");
      }
      setIsScanningLocal(false);
      setIsSubmitting(false);
      setProgress(null);
    }
  };

  return (
    <div className="bg-card border border-border rounded-xl p-6 shadow-sm card-hover-effect">
      <div className="flex items-start justify-between">
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
          disabled={isScanningLocal || isSubmitting}
          className={cn(
            "flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-all premium-glow",
            isScanningLocal || isSubmitting
              ? "bg-secondary text-muted-foreground cursor-not-allowed"
              : "bg-primary text-primary-foreground hover:bg-primary/90"
          )}
        >
          {isScanningLocal || isSubmitting ? (
            <Loader2 size={18} className="animate-spin" />
          ) : (
            <Play size={18} fill="currentColor" />
          )}
          {isScanningLocal ? "Reading Files..." : isSubmitting ? "Analyzing..." : "Select Folder"}
        </button>
      </div>

      {progress && isScanningLocal && (
        <div className="mt-6 space-y-2">
          <div className="flex justify-between text-xs text-muted-foreground">
            <span>{progress.status || "Scanning locally..."}</span>
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

      {error && (
        <div className="mt-4 p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-sm">
          {error}
        </div>
      )}
    </div>
  );
}
