"use client";

import { useState, useEffect } from "react";
import { CleanupPanel } from "@/components/CleanupPanel";
import { listCleanupSessions, executeCleanup, undoCleanup } from "@/lib/api";
import type { CleanupSession } from "@/lib/types";

export default function CleanupPage() {
  const [sessions, setSessions] = useState<CleanupSession[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchSessions = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await listCleanupSessions();
      setSessions(data);
    } catch (err: any) {
      setError(err.message || "Failed to load cleanup sessions");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchSessions();
  }, []);

  const handleExecute = async (id: string) => {
    try {
      await executeCleanup(id);
      await fetchSessions();
    } catch (err: any) {
      setError(err.message || "Execution failed");
    }
  };

  const handleUndo = async (id: string) => {
    try {
      await undoCleanup(id);
      await fetchSessions();
    } catch (err: any) {
      setError(err.message || "Undo failed");
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-700 ease-out">
      <div>
        <h1 className="text-3xl font-bold tracking-tight mb-2 text-foreground">Cleanup Operations</h1>
        <p className="text-muted-foreground">
          Review pending file deletions and undo recent mistakes.
        </p>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive">
          {error}
        </div>
      )}

      <CleanupPanel 
        sessions={sessions} 
        isLoading={isLoading} 
        onExecute={handleExecute}
        onUndo={handleUndo}
      />
    </div>
  );
}
