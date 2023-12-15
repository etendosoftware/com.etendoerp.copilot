/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        white: {
          900: "#FFFFFF",
        },
        gray: {
          100: "#FAFAFA",
          200: "#F2F5F9",
          300: "#F5F5FC",
          400: "#EFF1F7",
          500: "#E9EBf1",
          600: "#808695",
          700: "#666666",
          800: "#999999",
          900: "#333333",
        },
        blue: {
          200: "#EEEEFF",
          500: "#7182FF",
          900: "#202452",
        },
        black: {
          500: "#20232B",
          600: "#1E1F25",
          800: "#131517",
          900: "#000000",
        },
        sky: {
          500: "#3E97FF",
        },
        yellow: {
          300: "#FEF7D0",
          500: "#FAD614",
        },
        red: {
          200: "#E8CEE3",
        },
      },
    },
    screens: {
      md: "796px",
    },
  },
  plugins: [],
};
