"use client";

import { useState, useEffect } from "react";
import { Filter, Trash2 } from "lucide-react";
import { RecommendationsTable } from "@/components/RecommendationsTable";
import { getRecommendations, initiateCleanup } from "@/lib/api";
import type { Recommendation } from "@/lib/types";
import { cn } from "@/lib/utils";

const FILTER_TYPES = ["ALL", "DUPLICATE", "TEMP_FILE", "OLD_SCREENSHOT", "UNUSED_LARGE_FILE", "STALE_DOWNLOAD"];

export default function RecommendationsPage() {
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [isCleaning, setIsCleaning] = useState(false);

  useEffect(() => {
    const stored = localStorage.getItem("activeScanSession");
    if (stored) {
      try {
        const parsed = JSON.parse(stored);
        fetchRecommendations(parsed.id, typeFilter);
      } catch (e) {}
    }
  }, [typeFilter]);

  const fetchRecommendations = async (sessionId: number, type: string) => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getRecommendations(sessionId, type);
      setRecommendations(data.content || []);
      setSelectedIds(new Set()); // Reset selection on reload
    } catch (err: any) {
      setError(err.message || "Failed to load recommendations");
    } finally {
      setIsLoading(false);
    }
  };

  const handleToggleSelect = (id: number) => {
    const next = new Set(selectedIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelectedIds(next);
  };

  const handleToggleAll = () => {
    if (selectedIds.size === recommendations.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(recommendations.map(r => r.id)));
    }
  };

  const handleCleanup = async () => {
    if (selectedIds.size === 0) return;
    setIsCleaning(true);
    try {
      const fileIds = recommendations
        .filter(r => selectedIds.has(r.id))
        .map(r => r.fileId);
      
      await initiateCleanup(fileIds);
      
      // Reload recommendations after cleanup
      const stored = localStorage.getItem("activeScanSession");
      if (stored) {
        fetchRecommendations(JSON.parse(stored).id, typeFilter);
      }
    } catch (err: any) {
      setError(err.message || "Failed to initiate cleanup");
    } finally {
      setIsCleaning(false);
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-700 ease-out">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight mb-2 text-foreground">Recommendations</h1>
          <p className="text-muted-foreground">
            Smart suggestions for freeing up space and organizing your files.
          </p>
        </div>
        
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 bg-card border border-border px-3 py-1.5 rounded-lg shadow-sm">
            <Filter size={16} className="text-muted-foreground" />
            <select 
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              className="bg-transparent border-none text-sm font-medium focus:ring-0 outline-none text-foreground"
            >
              {FILTER_TYPES.map(t => (
                <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
              ))}
            </select>
          </div>
          
          <button
            onClick={handleCleanup}
            disabled={selectedIds.size === 0 || isCleaning}
            className={cn(
              "flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-all",
              selectedIds.size === 0 || isCleaning
                ? "bg-secondary text-muted-foreground cursor-not-allowed"
                : "bg-primary text-primary-foreground hover:bg-primary/90 premium-glow"
            )}
          >
            <Trash2 size={16} />
            {isCleaning ? "Processing..." : `Cleanup Selected (${selectedIds.size})`}
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive">
          {error}
        </div>
      )}

      <RecommendationsTable 
        recommendations={recommendations} 
        isLoading={isLoading} 
        selectedIds={selectedIds}
        onToggleSelect={handleToggleSelect}
        onToggleAll={handleToggleAll}
      />
    </div>
  );
}
