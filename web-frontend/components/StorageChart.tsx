"use client";

import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import { formatBytes } from "@/lib/filescanner";

interface Props {
  data: Array<{ name: string; value: number }>;
}

export function StorageChart({ data }: Props) {
  // If no data, show placeholder
  const chartData = data.length > 0 ? data : [
    { name: "Images", value: 0 },
    { name: "Videos", value: 0 },
    { name: "Documents", value: 0 },
    { name: "Archives", value: 0 },
    { name: "Media", value: 0 },
    { name: "Other", value: 0 },
  ];

  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-popover border border-border p-3 rounded-lg shadow-xl text-sm">
          <p className="font-medium text-foreground mb-1">{label}</p>
          <p className="text-primary font-semibold">
            {formatBytes(payload[0].value)}
          </p>
        </div>
      );
    }
    return null;
  };

  return (
    <div className="bg-card border border-border rounded-xl p-6 shadow-sm h-[350px]">
      <h3 className="text-sm font-medium text-muted-foreground mb-6">Storage Breakdown by File Type</h3>
      <div className="h-[250px] w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={chartData} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#272e3a" />
            <XAxis 
              dataKey="name" 
              axisLine={false}
              tickLine={false}
              tick={{ fill: "#94a3b8", fontSize: 12 }}
              dy={10}
            />
            <YAxis 
              axisLine={false}
              tickLine={false}
              tick={{ fill: "#94a3b8", fontSize: 12 }}
              tickFormatter={(val) => {
                if (val === 0) return "0";
                return formatBytes(val);
              }}
            />
            <Tooltip cursor={{ fill: "#1e293b" }} content={<CustomTooltip />} />
            <Bar 
              dataKey="value" 
              fill="#3b82f6" 
              radius={[4, 4, 0, 0]}
              animationDuration={1500}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
