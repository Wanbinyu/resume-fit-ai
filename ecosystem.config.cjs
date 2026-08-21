module.exports = {
  apps: [
    {
      name: "resume-fit-ai",
      script: "server.js",
      max_memory_restart: "400M",
      time: true,
      env: {
        NODE_ENV: "production",
        PORT: 3000
      }
    }
  ]
};
