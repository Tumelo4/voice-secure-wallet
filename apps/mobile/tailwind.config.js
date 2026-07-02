/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./App.{js,jsx,ts,tsx}", "./src/**/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {
      colors: {
        vault: {
          ink: "#13231f",
          moss: "#31432d",
          clay: "#bd6239",
          paper: "#f6efe1",
        },
      },
    },
  },
  plugins: [],
};
