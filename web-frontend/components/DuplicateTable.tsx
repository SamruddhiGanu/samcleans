"use client";

import { formatBytes } from "@/lib/filescanner";
import type { DuplicateGroup } from "@/lib/types";

interface Props {
  groups: DuplicateGroup[];
  isLoading: boolean;
}

export function DuplicateTable({ groups, isLoading }: Props) {
  if (isLoading) {
    return (
      <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden h-64 flex items-center justify-center">
        <div className="animate-pulse flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          <p className="text-muted-foreground text-sm font-medium">Detecting duplicates...</p>
        </div>
      </div>
    );
  }

  if (groups.length === 0) {
    return (
      <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden h-64 flex items-center justify-center">
        <p className="text-muted-foreground font-medium">No duplicates detected.</p>
      </div>
    );
  }

  return (
    <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm text-left">
          <thead className="bg-secondary/50 text-muted-foreground text-xs uppercase font-semibold">
            <tr>
              <th className="px-6 py-4">Hash Prefix</th>
              <th className="px-6 py-4">Copies</th>
              <th className="px-6 py-4">Total Size</th>
              <th className="px-6 py-4">Recoverable</th>
              <th className="px-6 py-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {groups.map((group, i) => (
              <tr key={i} className="hover:bg-secondary/30 transition-colors group">
                <td className="px-6 py-4 font-mono text-muted-foreground">
                  {group.hashValue}
                </td>
                <td className="px-6 py-4">
                  <span className="px-2.5 py-1 bg-primary/10 text-primary rounded-full font-medium">
                    {group.fileCount}
                  </span>
                </td>
                <td className="px-6 py-4">{formatBytes(group.totalSize)}</td>
                <td className="px-6 py-4 text-emerald-400 font-medium">
                  {formatBytes(group.recoverableSpace)}
                </td>
                <td className="px-6 py-4 text-right">
                  <button className="text-primary hover:text-primary-foreground font-medium text-xs px-3 py-1.5 rounded-md hover:bg-primary/10 transition-colors opacity-0 group-hover:opacity-100">
                    View Files
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
