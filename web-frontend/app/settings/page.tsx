"use client";

import { useState } from "react";
import { Save } from "lucide-react";
import { cn } from "@/lib/utils";

export default function SettingsPage() {
  // In a real app this might use localStorage or a settings context
  const [apiUrl, setApiUrl] = useState(
    typeof window !== "undefined" 
      ? localStorage.getItem("API_BASE_URL") || "http://localhost:8080"
      : "http://localhost:8080"
  );
  const [isSaved, setIsSaved] = useState(false);

  const handleSave = () => {
    localStorage.setItem("API_BASE_URL", apiUrl);
    setIsSaved(true);
    setTimeout(() => setIsSaved(false), 2000);
    // Reload to apply new URL
    window.location.reload();
  };

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700 ease-out">
      <div>
        <h1 className="text-3xl font-bold tracking-tight mb-2 text-foreground">Settings</h1>
        <p className="text-muted-foreground">
          Configure application preferences and connections.
        </p>
      </div>

      <div className="bg-card border border-border rounded-xl p-8 shadow-sm max-w-2xl">
        <h2 className="text-lg font-semibold mb-6">Backend Connection</h2>
        
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-muted-foreground mb-1.5">
              API Base URL
            </label>
            <input 
              type="text" 
              value={apiUrl}
              onChange={(e) => setApiUrl(e.target.value)}
              className="w-full bg-input border border-border rounded-lg px-4 py-2 text-foreground focus:ring-2 focus:ring-primary/50 outline-none transition-shadow"
              placeholder="http://localhost:8080"
            />
            <p className="text-xs text-muted-foreground mt-2">
              The URL of your Spring Boot backend server. Used when deploying to AWS.
            </p>
          </div>
          
          <div className="pt-4 border-t border-border/50 flex justify-end">
            <button
              onClick={handleSave}
              className={cn(
                "flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-all premium-glow",
                isSaved 
                  ? "bg-emerald-500 text-white" 
                  : "bg-primary text-primary-foreground hover:bg-primary/90"
              )}
            >
              <Save size={18} />
              {isSaved ? "Saved!" : "Save Settings"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
