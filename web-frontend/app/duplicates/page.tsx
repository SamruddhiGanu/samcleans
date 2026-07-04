"use client";

import { useState, useEffect } from "react";
import { Search } from "lucide-react";
import { DuplicateTable } from "@/components/DuplicateTable";
import { detectDuplicates } from "@/lib/api";
import { formatBytes } from "@/lib/filescanner";
import type { DuplicateAnalysis } from "@/lib/types";

export default function DuplicatesPage() {
  const [analysis, setAnalysis] = useState<DuplicateAnalysis | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Auto-detect if we have a session
    const stored = localStorage.getItem("activeScanSession");
    if (stored) {
      try {
        const parsed = JSON.parse(stored);
        handleDetect(parsed.id);
      } catch (e) {}
    }
  }, []);

  const handleDetect = async (sessionId: number) => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await detectDuplicates(sessionId);
      setAnalysis(data);
    } catch (err: any) {
      setError(err.message || "Failed to detect duplicates");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700 ease-out">
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight mb-2 text-foreground">Duplicate Explorer</h1>
          <p className="text-muted-foreground">
            Find and review identical files wasting space on your drive.
          </p>
        </div>
        
        {analysis && (
          <div className="bg-primary/10 border border-primary/20 text-primary px-4 py-2 rounded-lg text-sm font-medium flex items-center gap-2">
            <Search size={16} />
            {formatBytes(analysis.totalRecoverableSpace)} Recoverable
          </div>
        )}
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive">
          {error}
        </div>
      )}

      {!analysis && !isLoading && !error && (
        <div className="bg-card border border-border rounded-xl p-8 text-center shadow-sm">
          <p className="text-muted-foreground mb-4">No active scan session found.</p>
          <p className="text-sm">Please go to the Dashboard and scan a folder first.</p>
        </div>
      )}

      {(analysis || isLoading) && (
        <DuplicateTable groups={analysis?.groups || []} isLoading={isLoading} />
      )}
    </div>
  );
}
