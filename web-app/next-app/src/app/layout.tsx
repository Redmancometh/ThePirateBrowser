import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "The Pirate Browser",
  description: "Private torrent search and put.io control."
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
