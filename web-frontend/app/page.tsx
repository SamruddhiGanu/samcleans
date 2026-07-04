"use client";

import { useState, useEffect } from "react";
import { FilePicker } from "@/components/FilePicker";
import { HealthScoreCards } from "@/components/HealthScoreCards";
import { StorageChart } from "@/components/StorageChart";
import { getHealthScore } from "@/lib/api";
import type { ScanSession, HealthScore } from "@/lib/types";

export default function DashboardPage() {
  const [session, setSession] = useState<ScanSession | null>(null);
  const [scores, setScores] = useState<HealthScore | null>(null);
  const [isLoadingScores, setIsLoadingScores] = useState(false);

  useEffect(() => {
    // If we have a session in localStorage, restore it (simple global state for this demo)
    const stored = localStorage.getItem("activeScanSession");
    if (stored) {
      try {
        const parsed = JSON.parse(stored);
        setSession(parsed);
        fetchScores(parsed.id);
      } catch (e) {}
    }
  }, []);

  const handleScanComplete = async (newSession: ScanSession) => {
    setSession(newSession);
    localStorage.setItem("activeScanSession", JSON.stringify(newSession));
    await fetchScores(newSession.id);
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

  // Mock chart data derived from total size (since backend doesn't return breakdown yet)
  // In a real implementation, you'd fetch this from the backend.
  const chartData = session && session.totalSize ? [
    { name: "Images", value: session.totalSize * 0.4 },
    { name: "Videos", value: session.totalSize * 0.3 },
    { name: "Documents", value: session.totalSize * 0.1 },
    { name: "Archives", value: session.totalSize * 0.05 },
    { name: "Media", value: session.totalSize * 0.1 },
    { name: "Other", value: session.totalSize * 0.05 },
  ] : [];

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
