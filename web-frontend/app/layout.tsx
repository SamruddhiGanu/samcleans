import type { Metadata } from "next";
import { Sidebar } from "@/components/Sidebar";
import "./globals.css";

export const metadata: Metadata = {
  title: "Storage Health Ranker",
  description: "AI-powered storage analysis tool",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="dark">
      <body className="flex min-h-screen bg-background text-foreground antialiased selection:bg-primary/30">
        <Sidebar />
        <main className="flex-1 flex flex-col h-screen overflow-hidden">
          <div className="flex-1 overflow-y-auto p-8 relative">
            {/* Ambient background glow */}
            <div className="absolute top-0 left-1/4 w-1/2 h-64 bg-primary/5 rounded-full blur-[100px] pointer-events-none" />
            
            <div className="max-w-6xl mx-auto relative z-10">
              {children}
            </div>
          </div>
        </main>
      </body>
    </html>
  );
}
