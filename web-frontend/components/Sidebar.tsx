"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LayoutDashboard, Copy, Lightbulb, Trash2, Settings, HardDrive } from "lucide-react";
import { cn } from "@/lib/utils";

const NAV_ITEMS = [
  { name: "Dashboard", href: "/", icon: LayoutDashboard },
  { name: "Duplicates", href: "/duplicates", icon: Copy },
  { name: "Recommendations", href: "/recommendations", icon: Lightbulb },
  { name: "Cleanup", href: "/cleanup", icon: Trash2 },
  { name: "Settings", href: "/settings", icon: Settings },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <div className="w-64 h-screen bg-card border-r border-border flex flex-col flex-shrink-0">
      <div className="p-6 flex items-center gap-3">
        <div className="w-8 h-8 rounded-lg bg-primary/20 flex items-center justify-center text-primary">
          <HardDrive size={20} />
        </div>
        <span className="font-semibold text-lg tracking-tight">Storage Health</span>
      </div>

      <nav className="flex-1 px-4 py-4 space-y-1">
        {NAV_ITEMS.map((item) => {
          const isActive = pathname === item.href;
          const Icon = item.icon;
          return (
            <Link
              key={item.name}
              href={item.href}
              className={cn(
                "flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors",
                isActive
                  ? "bg-primary/10 text-primary"
                  : "text-muted-foreground hover:bg-secondary hover:text-foreground"
              )}
            >
              <Icon size={18} className={isActive ? "text-primary" : "text-muted-foreground"} />
              {item.name}
            </Link>
          );
        })}
      </nav>

      <div className="p-4 border-t border-border/50">
        <div className="bg-secondary/50 rounded-lg p-4 text-xs text-muted-foreground">
          <p className="font-medium text-foreground mb-1">Local Storage Scanner</p>
          <p>Powered by Next.js & Spring Boot</p>
        </div>
      </div>
    </div>
  );
}
