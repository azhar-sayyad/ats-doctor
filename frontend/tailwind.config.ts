import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './app/**/*.{ts,tsx}',
    './components/**/*.{ts,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: '#3157f6',
          soft: '#eff3ff',
          hover: '#2544c9',
        },
        proof: {
          DEFAULT: '#cff52b',
          ink: '#111d04',
        },
        surface: {
          DEFAULT: '#faf9f6',
          secondary: '#f4f3ef',
        },
        foreground: '#111318',
        faint: '#8e95a5',
        muted: '#525866',
        coral: {
          DEFAULT: '#ff4d4d',
          soft: '#fff0f0',
        },
      },
      fontFamily: {
        sans: ['var(--font-geist-sans)', 'system-ui', 'sans-serif'],
        mono: ['var(--font-geist-mono)', 'monospace'],
        editorial: ['var(--font-instrument-serif)', 'serif'],
      },
      animation: {
        'scan-beam': 'scanBeam 3.5s ease-in-out infinite',
        'float-slow': 'floatSlow 7s ease-in-out infinite',
        'float-reverse': 'floatReverse 8s ease-in-out infinite',
        'pulse-subtle': 'pulseSubtle 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      },
      keyframes: {
        scanBeam: {
          '0%, 100%': { transform: 'translateY(-10%)', opacity: '0.2' },
          '50%': { transform: 'translateY(110%)', opacity: '1' },
        },
        floatSlow: {
          '0%, 100%': { transform: 'translateY(0px) rotate(-6deg)' },
          '50%': { transform: 'translateY(-12px) rotate(-4deg)' },
        },
        floatReverse: {
          '0%, 100%': { transform: 'translateY(0px) rotate(0deg)' },
          '50%': { transform: 'translateY(10px) rotate(1deg)' },
        },
        pulseSubtle: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.5' },
        },
      },
    },
  },
  plugins: [],
};

export default config;

