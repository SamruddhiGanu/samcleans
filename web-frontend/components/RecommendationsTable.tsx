"use client";

import { formatBytes } from "@/lib/filescanner";
import type { Recommendation } from "@/lib/types";

interface Props {
  recommendations: Recommendation[];
  isLoading: boolean;
  selectedIds: Set<number>;
  onToggleSelect: (id: number) => void;
  onToggleAll: () => void;
}

export function RecommendationsTable({ 
  recommendations, isLoading, selectedIds, onToggleSelect, onToggleAll 
}: Props) {
  
  if (isLoading) {
    return (
      <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden h-64 flex items-center justify-center">
        <div className="animate-pulse flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          <p className="text-muted-foreground text-sm font-medium">Loading recommendations...</p>
        </div>
      </div>
    );
  }

  if (recommendations.length === 0) {
    return (
      <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden h-64 flex items-center justify-center">
        <p className="text-muted-foreground font-medium">No recommendations found.</p>
      </div>
    );
  }

  const allSelected = recommendations.length > 0 && selectedIds.size === recommendations.length;

  return (
    <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm text-left">
          <thead className="bg-secondary/50 text-muted-foreground text-xs uppercase font-semibold">
            <tr>
              <th className="px-4 py-4 w-12 text-center">
                <input 
                  type="checkbox" 
                  checked={allSelected}
                  onChange={onToggleAll}
                  className="rounded border-border bg-input text-primary focus:ring-primary/30"
                />
              </th>
              <th className="px-4 py-4">Type</th>
              <th className="px-4 py-4">File Name</th>
              <th className="px-4 py-4">Confidence</th>
              <th className="px-4 py-4">Recoverable</th>
              <th className="px-4 py-4">Explanation</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {recommendations.map((rec) => (
              <tr 
                key={rec.id} 
                className={`transition-colors ${selectedIds.has(rec.id) ? 'bg-primary/5' : 'hover:bg-secondary/30'}`}
                onClick={() => onToggleSelect(rec.id)}
              >
                <td className="px-4 py-4 text-center">
                  <input 
                    type="checkbox" 
                    checked={selectedIds.has(rec.id)}
                    onChange={() => {}} // handled by row click
                    className="rounded border-border bg-input text-primary focus:ring-primary/30 pointer-events-none"
                  />
                </td>
                <td className="px-4 py-4">
                  <span className="px-2.5 py-1 bg-secondary text-foreground rounded-full text-xs font-medium border border-border">
                    {rec.type.replace(/_/g, ' ')}
                  </span>
                </td>
                <td className="px-4 py-4 text-muted-foreground font-medium max-w-[200px] truncate" title={rec.filePath}>
                  {rec.fileName}
                </td>
                <td className="px-4 py-4">
                  <div className="flex items-center gap-2">
                    <div className="w-16 h-2 bg-secondary rounded-full overflow-hidden">
                      <div 
                        className="h-full bg-primary" 
                        style={{ width: `${rec.confidenceScore * 100}%` }}
                      />
                    </div>
                    <span className="text-xs text-muted-foreground">
                      {Math.round(rec.confidenceScore * 100)}%
                    </span>
                  </div>
                </td>
                <td className="px-4 py-4 text-emerald-400 font-medium">
                  {rec.recoverableSpace ? formatBytes(rec.recoverableSpace) : '—'}
                </td>
                <td className="px-4 py-4 text-muted-foreground text-xs leading-relaxed max-w-[300px]">
                  {rec.explanation}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
