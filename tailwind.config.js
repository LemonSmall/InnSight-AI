/** @type {import('tailwindcss').Config} */

export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{js,ts,vue}"],
  theme: {
    container: {
      center: true,
    },
    extend: {
      fontFamily: {
        serif: ['"Noto Serif SC"', 'serif'],
        sans: ['"Noto Sans SC"', 'sans-serif'],
      },
      colors: {
        bamboo: {
          50: '#F2F7EE',
          100: '#E3EFDC',
          200: '#C7DFB9',
          300: '#A3CF8F',
          400: '#7FBF65',
          500: '#4A7C59',
          600: '#3D6B4A',
          700: '#2F573A',
          800: '#23442C',
          900: '#1A3C2A',
          950: '#0F2A1C',
        },
        cream: {
          50: '#FDFCFB',
          100: '#FAF8F5',
          200: '#F5F0E8',
          300: '#EDE5D8',
          400: '#E0D5C0',
        },
        warm: {
          400: '#C4953A',
          500: '#B08530',
          600: '#8B7355',
          700: '#6B5940',
        },
        rose: {
          400: '#C46B5A',
          500: '#B05A4A',
        },
        brand: {
          50: '#F0EBFE',
          100: '#E4D9FC',
          200: '#C4B0EE',
          300: '#A98AEC',
          400: '#8B7AE8',
          500: '#6C4EE0',
          600: '#5B3DD4',
          700: '#5A3EC8',
          800: '#8A48A4',
        },
      },
    },
  },
  plugins: [],
};
