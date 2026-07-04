"use client";

import { formatBytes } from "@/lib/filescanner";
import type { CleanupSession } from "@/lib/types";
import { Play, RotateCcw } from "lucide-react";
import { cn } from "@/lib/utils";

interface Props {
  sessions: CleanupSession[];
  isLoading: boolean;
  onExecute: (id: string) => void;
  onUndo: (id: string) => void;
}

export function CleanupPanel({ sessions, isLoading, onExecute, onUndo }: Props) {
  
  if (isLoading) {
    return (
      <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden h-64 flex items-center justify-center">
        <div className="animate-pulse flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          <p className="text-muted-foreground text-sm font-medium">Loading sessions...</p>
        </div>
      </div>
    );
  }

  if (sessions.length === 0) {
    return (
      <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden h-64 flex items-center justify-center">
        <p className="text-muted-foreground font-medium">No cleanup sessions found.</p>
      </div>
    );
  }

  return (
    <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm text-left">
          <thead className="bg-secondary/50 text-muted-foreground text-xs uppercase font-semibold">
            <tr>
              <th className="px-6 py-4">Session ID</th>
              <th className="px-6 py-4">Status</th>
              <th className="px-6 py-4">Files</th>
              <th className="px-6 py-4">Space Freed</th>
              <th className="px-6 py-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {sessions.map((session) => (
              <tr key={session.sessionId} className="hover:bg-secondary/30 transition-colors group">
                <td className="px-6 py-4 font-mono text-muted-foreground text-xs truncate max-w-[150px]">
                  {session.sessionId}
                </td>
                <td className="px-6 py-4">
                  <span className={cn(
                    "px-2.5 py-1 rounded-full text-xs font-medium border",
                    session.status === 'PENDING' ? "bg-amber-400/10 text-amber-400 border-amber-400/20" :
                    session.status === 'COMPLETED' ? "bg-emerald-400/10 text-emerald-400 border-emerald-400/20" :
                    "bg-secondary text-muted-foreground border-border"
                  )}>
                    {session.status}
                  </span>
                </td>
                <td className="px-6 py-4 font-medium">{session.filesCount}</td>
                <td className="px-6 py-4 text-emerald-400 font-medium">
                  {formatBytes(session.totalSize)}
                </td>
                <td className="px-6 py-4 text-right">
                  <div className="flex justify-end gap-2">
                    {session.status === 'PENDING' && (
                      <button 
                        onClick={() => onExecute(session.sessionId)}
                        className="flex items-center gap-1.5 text-primary hover:text-primary-foreground font-medium text-xs px-3 py-1.5 rounded-md hover:bg-primary/20 transition-colors"
                      >
                        <Play size={14} /> Execute
                      </button>
                    )}
                    {session.status === 'COMPLETED' && (
                      <button 
                        onClick={() => onUndo(session.sessionId)}
                        className="flex items-center gap-1.5 text-amber-400 hover:text-amber-300 font-medium text-xs px-3 py-1.5 rounded-md hover:bg-amber-400/10 transition-colors"
                      >
                        <RotateCcw size={14} /> Undo
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
