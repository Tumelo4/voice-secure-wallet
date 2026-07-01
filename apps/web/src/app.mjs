import {
  createDashboardModel,
  renderPhaseTimeline,
  renderRiskPanel,
  renderSummaryCards,
  renderTestSuites,
} from "./dashboard.mjs";

const model = createDashboardModel();

document.querySelector("[data-summary]").innerHTML = renderSummaryCards(model);
document.querySelector("[data-phases]").innerHTML = renderPhaseTimeline(model);
document.querySelector("[data-risks]").innerHTML = renderRiskPanel(model);
document.querySelector("[data-tests]").innerHTML = renderTestSuites(model);
document.querySelector("[data-generated-at]").textContent = new Intl.DateTimeFormat("en-ZA", {
  dateStyle: "medium",
  timeStyle: "short",
}).format(new Date(model.generatedAt));
