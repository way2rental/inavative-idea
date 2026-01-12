/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        // Global Semantic Colors - Manageable from here
        primary: {
          DEFAULT: '#97144D', // axis-burgundy
          50: '#fdf2f8',
          100: '#fce7f3',
          200: '#fbcfe8',
          300: '#f9a8d4',
          400: '#f472b6',
          500: '#97144D', // axis-burgundy
          600: '#6D0F37', // axis-maroon
          700: '#5a0e2d',
          800: '#4a0c24',
          900: '#3a091b',
        },
        secondary: {
          DEFAULT: '#6D0F37', // axis-maroon
          50: '#f9fafb',
          100: '#f3f4f6',
          200: '#e5e7eb',
          300: '#d1d5db',
          400: '#9ca3af',
          500: '#6b7280',
          600: '#4b5563',
          700: '#374151',
          800: '#1f2937',
          900: '#111827',
        },
        action: {
          DEFAULT: '#97144D', // Primary action button
          hover: '#6D0F37',
          light: '#B91C5E',
        },
        success: {
          DEFAULT: '#10b981',
          light: '#d1fae5',
          dark: '#059669',
        },
        warning: {
          DEFAULT: '#f59e0b',
          light: '#fef3c7',
          dark: '#d97706',
        },
        danger: {
          DEFAULT: '#ef4444',
          light: '#fee2e2',
          dark: '#dc2626',
        },
        info: {
          DEFAULT: '#3b82f6',
          light: '#dbeafe',
          dark: '#2563eb',
        },
        // Axis Bank Brand Colors (kept for backward compatibility)
        axis: {
          burgundy: '#97144D',
          maroon: '#6D0F37',
          lightBurgundy: '#B91C5E',
        },
        'axis-burgundy': '#97144D',
        'axis-maroon': '#6D0F37',
        'bank-bg': '#F5F5F5',
      },
      fontFamily: {
        sans: ['Roboto', 'Segoe UI', 'Arial', 'sans-serif'],
        heading: ['Montserrat', 'sans-serif'],
      },
      boxShadow: {
        'card': '0 2px 8px rgba(0, 0, 0, 0.08)',
        'elevated': '0 8px 24px rgba(0, 0, 0, 0.12)',
        'chat': '0 12px 32px rgba(151, 20, 77, 0.15)',
      },
      animation: {
        'slide-up': 'slideUp 0.3s ease-out',
      },
      keyframes: {
        slideUp: {
          '0%': { transform: 'translateY(20px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
      },
    },
  },
  plugins: [],
}

