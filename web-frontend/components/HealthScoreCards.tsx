import { Activity, CopyX, Sparkles, LayoutGrid } from "lucide-react";
import type { HealthScore } from "@/lib/types";
import { cn } from "@/lib/utils";
import { formatBytes } from "@/lib/filescanner";

interface Props {
  scores: HealthScore | null;
  isLoading: boolean;
}

export function HealthScoreCards({ scores, isLoading }: Props) {
  // duplicateWasteScore = 100 means 0% waste (perfectly clean)
  // We display "waste %" = 100 - duplicateWasteScore
  const duplicateWastePct = scores ? Math.round(100 - scores.duplicateWasteScore) : null;
  const clutterPct        = scores ? Math.round(100 - scores.clutterScore) : null;

  const cards = [
    {
      title: "Overall Health",
      value: scores ? Math.round(scores.overallScore) : "—",
      suffix: scores ? "/100" : "",
      subtitle: scores
        ? scores.overallScore >= 80 ? "Excellent" : scores.overallScore >= 60 ? "Good" : scores.overallScore >= 40 ? "Fair" : "Needs attention"
        : "Run a scan",
      icon: Activity,
      color: scores
        ? scores.overallScore >= 80 ? "text-emerald-400" : scores.overallScore >= 60 ? "text-green-400" : scores.overallScore >= 40 ? "text-amber-400" : "text-red-400"
        : "text-muted-foreground",
      bg: scores
        ? scores.overallScore >= 80 ? "bg-emerald-400/10" : scores.overallScore >= 60 ? "bg-green-400/10" : scores.overallScore >= 40 ? "bg-amber-400/10" : "bg-red-400/10"
        : "bg-muted/10",
      border: scores
        ? scores.overallScore >= 80 ? "border-emerald-400/20" : scores.overallScore >= 60 ? "border-green-400/20" : scores.overallScore >= 40 ? "border-amber-400/20" : "border-red-400/20"
        : "border-border",
    },
    {
      title: "Duplicate Waste",
      value: duplicateWastePct ?? "—",
      suffix: duplicateWastePct != null ? "%" : "",
      // 0% = no duplicates (good), 100% = all files are duplicates (bad)
      subtitle: duplicateWastePct != null
        ? duplicateWastePct === 0 ? "No duplicates found" : `${formatBytes(scores?.duplicateWaste ?? 0)} recoverable`
        : "Run a scan",
      icon: CopyX,
      color: duplicateWastePct != null
        ? duplicateWastePct === 0 ? "text-emerald-400" : duplicateWastePct < 10 ? "text-green-400" : duplicateWastePct < 30 ? "text-amber-400" : "text-red-400"
        : "text-muted-foreground",
      bg: duplicateWastePct != null
        ? duplicateWastePct === 0 ? "bg-emerald-400/10" : duplicateWastePct < 10 ? "bg-green-400/10" : duplicateWastePct < 30 ? "bg-amber-400/10" : "bg-red-400/10"
        : "bg-muted/10",
      border: duplicateWastePct != null
        ? duplicateWastePct === 0 ? "border-emerald-400/20" : duplicateWastePct < 10 ? "border-green-400/20" : duplicateWastePct < 30 ? "border-amber-400/20" : "border-red-400/20"
        : "border-border",
    },
    {
      title: "Clutter",
      value: clutterPct ?? "—",
      suffix: clutterPct != null ? "%" : "",
      subtitle: clutterPct != null
        ? clutterPct === 0 ? "No temp/cache files" : `${formatBytes(scores?.clutteredSize ?? 0)} temp/cache`
        : "Run a scan",
      icon: Sparkles,
      color: clutterPct != null
        ? clutterPct === 0 ? "text-emerald-400" : clutterPct < 10 ? "text-blue-400" : clutterPct < 30 ? "text-amber-400" : "text-red-400"
        : "text-muted-foreground",
      bg: clutterPct != null
        ? clutterPct === 0 ? "bg-emerald-400/10" : clutterPct < 10 ? "bg-blue-400/10" : clutterPct < 30 ? "bg-amber-400/10" : "bg-red-400/10"
        : "bg-muted/10",
      border: clutterPct != null
        ? clutterPct === 0 ? "border-emerald-400/20" : clutterPct < 10 ? "border-blue-400/20" : clutterPct < 30 ? "border-amber-400/20" : "border-red-400/20"
        : "border-border",
    },
    {
      title: "Organisation",
      value: scores ? Math.round(scores.organizationScore) : "—",
      suffix: scores ? "/100" : "",
      subtitle: scores
        ? scores.organizationScore >= 80 ? "Well organised" : scores.organizationScore >= 60 ? "Moderately organised" : "Could be better"
        : "Run a scan",
      icon: LayoutGrid,
      color: scores
        ? scores.organizationScore >= 60 ? "text-purple-400" : "text-amber-400"
        : "text-muted-foreground",
      bg: scores
        ? scores.organizationScore >= 60 ? "bg-purple-400/10" : "bg-amber-400/10"
        : "bg-muted/10",
      border: scores
        ? scores.organizationScore >= 60 ? "border-purple-400/20" : "border-amber-400/20"
        : "border-border",
    },
  ];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      {cards.map((card, i) => {
        const Icon = card.icon;
        return (
          <div
            key={i}
            className={cn(
              "bg-card rounded-xl p-5 border shadow-sm transition-all duration-500",
              card.border,
              isLoading ? "animate-pulse" : "card-hover-effect"
            )}
          >
            <div className="flex items-center justify-between mb-4">
              <span className="text-sm font-medium text-muted-foreground">{card.title}</span>
              <div className={cn("p-2 rounded-lg", card.bg, card.color)}>
                <Icon size={18} />
              </div>
            </div>

            <div className="flex items-baseline gap-1 mb-1">
              <span className={cn("text-3xl font-bold tracking-tight", card.color)}>
                {card.value}
              </span>
              <span className="text-sm font-medium text-muted-foreground">
                {card.suffix}
              </span>
            </div>

            <p className="text-xs text-muted-foreground/70 mt-1 leading-tight">
              {isLoading ? "Calculating…" : card.subtitle}
            </p>
          </div>
        );
      })}
    </div>
  );
}
