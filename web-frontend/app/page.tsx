"use client";

import { useState, useEffect, useCallback } from "react";
import { FilePicker } from "@/components/FilePicker";
import { HealthScoreCards } from "@/components/HealthScoreCards";
import { StorageChart } from "@/components/StorageChart";
import { getHealthScore, getBreakdown, getScanSession, detectDuplicates, sessionExists } from "@/lib/api";
import type { ScanSession, HealthScore } from "@/lib/types";
import { formatBytes } from "@/lib/filescanner";
import { RefreshCw, FolderOpen, Clock, Files, HardDrive, X } from "lucide-react";

/** Maps backend FileType enum names to user-friendly display labels. */
const FILE_TYPE_LABELS: Record<string, string> = {
  IMAGE:      "Images",
  VIDEO:      "Videos",
  DOCUMENT:   "Documents",
  ARCHIVE:    "Archives",
  EXECUTABLE: "Executables",
  MEDIA:      "Audio",
  TEMPORARY:  "Temp/Cache",
  OTHER:      "Other",
};

export default function DashboardPage() {
  const [session, setSession] = useState<ScanSession | null>(null);
  const [scores, setScores] = useState<HealthScore | null>(null);
  const [isLoadingScores, setIsLoadingScores] = useState(false);
  const [chartData, setChartData] = useState<Array<{ name: string; value: number }>>([]);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const fetchScores = useCallback(async (sessionId: number) => {
    setIsLoadingScores(true);
    try {
      const s = await getHealthScore(sessionId);
      setScores(s);
    } catch (e) {
      console.error("Failed to fetch health scores", e);
    } finally {
      setIsLoadingScores(false);
    }
  }, []);

  const fetchBreakdown = useCallback(async (sessionId: number) => {
    try {
      const breakdown = await getBreakdown(sessionId);
      const data = Object.entries(breakdown.sizeByType)
        .filter(([, bytes]) => bytes > 0)
        .sort(([, a], [, b]) => b - a)
        .map(([type, bytes]) => ({
          name: FILE_TYPE_LABELS[type] ?? type,
          value: bytes,
        }));
      setChartData(data);
    } catch (e) {
      console.error("Failed to fetch file type breakdown", e);
    }
  }, []);

  useEffect(() => {
    const stored = localStorage.getItem("activeScanSession");
    if (stored) {
      try {
        const parsed = JSON.parse(stored);
        sessionExists(parsed.id)
          .then((exists) => {
            if (!exists) {
              localStorage.removeItem("activeScanSession");
              return null;
            }
            return getScanSession(parsed.id);
          })
          .then((live) => {
            if (!live) return;
            setSession(live);
            void fetchScores(live.id);
            void fetchBreakdown(live.id);
          })
          .catch(() => {
            localStorage.removeItem("activeScanSession");
          });
      } catch (e) {
        localStorage.removeItem("activeScanSession");
      }
    }
  }, [fetchScores, fetchBreakdown]);

  const handleScanComplete = async (newSession: ScanSession) => {
    setSession(newSession);
    setScores(null);
    setChartData([]);
    localStorage.setItem("activeScanSession", JSON.stringify(newSession));

    await Promise.all([
      fetchScores(newSession.id),
      fetchBreakdown(newSession.id),
    ]);
  };

  const handleRefresh = async () => {
    if (!session) return;
    setIsRefreshing(true);
    try {
      // Re-trigger duplicate detection, then refresh scores
      await detectDuplicates(session.id);
      await Promise.all([fetchScores(session.id), fetchBreakdown(session.id)]);
    } catch (e) {
      // If duplicate detection fails, still refresh scores
      await Promise.all([fetchScores(session.id), fetchBreakdown(session.id)]);
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleClearSession = () => {
    setSession(null);
    setScores(null);
    setChartData([]);
    localStorage.removeItem("activeScanSession");
  };

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700 ease-out">
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight mb-2 text-foreground">Dashboard</h1>
          <p className="text-muted-foreground">
            Analyze your local storage health and get cleanup recommendations.
          </p>
        </div>
        {session && (
          <div className="flex items-center gap-2">
            <button
              onClick={handleRefresh}
              disabled={isRefreshing || isLoadingScores}
              title="Re-analyze scores"
              className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium border border-border bg-secondary text-secondary-foreground hover:bg-secondary/80 transition-all disabled:opacity-50"
            >
              <RefreshCw size={14} className={isRefreshing ? "animate-spin" : ""} />
              {isRefreshing ? "Re-analyzing..." : "Refresh"}
            </button>
            <button
              onClick={handleClearSession}
              title="Clear current session to scan a new folder"
              className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium border border-border bg-secondary text-secondary-foreground hover:bg-secondary/80 transition-all"
            >
              <X size={14} />
              New Scan
            </button>
          </div>
        )}
      </div>

      {/* Active Session Info Banner */}
      {session && (
        <div className="bg-card border border-border rounded-xl px-5 py-4 shadow-sm flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
          <div className="flex items-center gap-2 text-primary font-semibold">
            <FolderOpen size={16} />
            <span className="truncate max-w-xs">{session.sessionName}</span>
          </div>
          {session.totalFiles != null && (
            <div className="flex items-center gap-1.5 text-muted-foreground">
              <Files size={14} />
              <span>{session.totalFiles.toLocaleString()} files</span>
            </div>
          )}
          {session.totalSize != null && session.totalSize > 0 && (
            <div className="flex items-center gap-1.5 text-muted-foreground">
              <HardDrive size={14} />
              <span>{formatBytes(session.totalSize)}</span>
            </div>
          )}
          {session.endTime && (
            <div className="flex items-center gap-1.5 text-muted-foreground">
              <Clock size={14} />
              <span>Scanned {new Date(session.endTime).toLocaleString()}</span>
            </div>
          )}
          <div className={`ml-auto flex items-center gap-1.5 text-xs px-2 py-1 rounded-full font-medium ${
            session.status === "COMPLETED"
              ? "bg-emerald-400/10 text-emerald-400 border border-emerald-400/20"
              : "bg-amber-400/10 text-amber-400 border border-amber-400/20"
          }`}>
            <span className={`w-1.5 h-1.5 rounded-full ${session.status === "COMPLETED" ? "bg-emerald-400" : "bg-amber-400"}`} />
            {session.status}
          </div>
        </div>
      )}

      <FilePicker onScanComplete={handleScanComplete} hasActiveSession={!!session} />

      <HealthScoreCards scores={scores} isLoading={isLoadingScores} />

      <StorageChart data={chartData} />
    </div>
  );
}
