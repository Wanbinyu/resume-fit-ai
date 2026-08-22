fetch("/api/public-config")
  .then((response) => {
    if (!response.ok) throw new Error("Public config unavailable");
    return response.json();
  })
  .then(({ operatorName, contact }) => {
    document.querySelectorAll("[data-operator]").forEach((element) => {
      element.textContent = operatorName;
    });
    document.querySelectorAll("[data-contact]").forEach((element) => {
      element.replaceChildren(buildContactNode(contact));
    });
  })
  .catch(() => {});

function buildContactNode(contact) {
  const value = String(contact || "").slice(0, 200);
  const link = document.createElement("a");
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    link.href = `mailto:${value}`;
    link.textContent = value;
    return link;
  }
  if (/^https:\/\//i.test(value)) {
    link.href = value;
    link.textContent = value;
    link.target = "_blank";
    link.rel = "noopener";
    return link;
  }
  return document.createTextNode(value || "请以正式站点公示信息为准");
}
