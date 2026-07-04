"use client";

import { useState, useEffect } from "react";
import { FilePicker } from "@/components/FilePicker";
import { HealthScoreCards } from "@/components/HealthScoreCards";
import { StorageChart } from "@/components/StorageChart";
import { getHealthScore, getBreakdown } from "@/lib/api";
import type { ScanSession, HealthScore } from "@/lib/types";

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

  useEffect(() => {
    // If we have a session in localStorage, restore it (simple global state for this demo)
    const stored = localStorage.getItem("activeScanSession");
    if (stored) {
      try {
        const parsed = JSON.parse(stored);
        setSession(parsed);
        fetchScores(parsed.id);
        fetchBreakdown(parsed.id);
      } catch (e) {}
    }
  }, []);

  const handleScanComplete = async (newSession: ScanSession) => {
    setSession(newSession);
    localStorage.setItem("activeScanSession", JSON.stringify(newSession));
    await Promise.all([
      fetchScores(newSession.id),
      fetchBreakdown(newSession.id),
    ]);
  };

  const fetchScores = async (sessionId: number) => {
    setIsLoadingScores(true);
    try {
      const s = await getHealthScore(sessionId);
      setScores(s);
    } catch (e) {
      console.error("Failed to fetch health scores", e);
    } finally {
      setIsLoadingScores(false);
    }
  };

  const fetchBreakdown = async (sessionId: number) => {
    try {
      const breakdown = await getBreakdown(sessionId);
      // Convert { IMAGE: 1048576, VIDEO: 524288, ... } → chart-friendly array
      // Filter out types with 0 bytes and sort by size descending
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
  };

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700 ease-out">
      <div>
        <h1 className="text-3xl font-bold tracking-tight mb-2 text-foreground">Dashboard</h1>
        <p className="text-muted-foreground">
          Analyze your local storage health and get cleanup recommendations.
        </p>
      </div>

      <FilePicker onScanComplete={handleScanComplete} />

      <HealthScoreCards scores={scores} isLoading={isLoadingScores} />

      <StorageChart data={chartData} />
    </div>
  );
}
