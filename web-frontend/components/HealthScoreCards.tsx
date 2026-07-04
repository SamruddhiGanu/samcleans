import { Activity, CopyX, Sparkles, LayoutGrid } from "lucide-react";
import type { HealthScore } from "@/lib/types";
import { cn } from "@/lib/utils";

interface Props {
  scores: HealthScore | null;
  isLoading: boolean;
}

export function HealthScoreCards({ scores, isLoading }: Props) {
  const cards = [
    {
      title: "Overall Health",
      value: scores ? Math.round(scores.overallScore) : "—",
      suffix: "/100",
      icon: Activity,
      color: "text-emerald-400",
      bg: "bg-emerald-400/10",
      border: "border-emerald-400/20",
    },
    {
      title: "Duplicate Waste",
      value: scores ? Math.round(100 - scores.duplicateWasteScore) : "—",
      suffix: "%",
      icon: CopyX,
      color: "text-amber-400",
      bg: "bg-amber-400/10",
      border: "border-amber-400/20",
    },
    {
      title: "Clutter",
      value: scores ? Math.round(100 - scores.clutterScore) : "—",
      suffix: "%",
      icon: Sparkles,
      color: "text-blue-400",
      bg: "bg-blue-400/10",
      border: "border-blue-400/20",
    },
    {
      title: "Organisation",
      value: scores ? Math.round(scores.organizationScore) : "—",
      suffix: "/100",
      icon: LayoutGrid,
      color: "text-purple-400",
      bg: "bg-purple-400/10",
      border: "border-purple-400/20",
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
            
            <div className="flex items-baseline gap-1">
              <span className={cn("text-3xl font-bold tracking-tight", scores ? card.color : "text-muted")}>
                {card.value}
              </span>
              <span className="text-sm font-medium text-muted-foreground">
                {scores ? card.suffix : ""}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
