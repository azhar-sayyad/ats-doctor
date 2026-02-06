import type { Metadata } from 'next';
import { Inter, JetBrains_Mono, Instrument_Serif } from 'next/font/google';
import Navbar from '../components/Navbar';
import './globals.css';

const sansFont = Inter({
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-geist-sans',
});

const monoFont = JetBrains_Mono({
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-geist-mono',
});

const editorialFont = Instrument_Serif({
  weight: ['400'],
  subsets: ['latin'],
  style: ['normal', 'italic'],
  display: 'swap',
  variable: '--font-instrument-serif',
});

export const metadata: Metadata = {
  title: 'ATS Doctor — Tailor every application with precision',
  description: 'Turn your master resume into role-specific, evidence-backed ATS rewrites with match scoring and review.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="en"
      className={`${sansFont.variable} ${monoFont.variable} ${editorialFont.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col bg-white text-foreground font-sans selection:bg-proof selection:text-proof-ink">
        <Navbar />
        <main className="flex-1 w-full">{children}</main>
      </body>
    </html>
  );
}