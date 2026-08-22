module.exports = {
  apps: [
    {
      name: "resume-fit-ai",
      script: "server.js",
      instances: 1,
      exec_mode: "fork",
      max_memory_restart: "400M",
      kill_timeout: 12000,
      time: true,
      env: {
        NODE_ENV: "production",
        PORT: 3000
      }
    }
  ]
};
