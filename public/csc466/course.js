for (const card of document.querySelectorAll("div.kp")) {
  const heading = card.querySelector(":scope > h3");
  const caption = card.querySelector(":scope > p");
  const detail = card.querySelector(":scope > .detail");
  if (!heading || !caption || !detail) continue;

  const disclosure = document.createElement("details");
  disclosure.className = "kp";
  const summary = document.createElement("summary");
  const title = document.createElement("strong");
  title.textContent = heading.textContent;
  const description = document.createElement("span");
  description.textContent = caption.textContent;
  summary.append(title, description);
  disclosure.append(summary, detail);
  card.replaceWith(disclosure);
}

for (const quiz of document.querySelectorAll(".quiz")) {
  const options = [...quiz.querySelectorAll(".option")];
  const explanation = quiz.querySelector(".explain");
  if (!explanation) continue;

  explanation.hidden = true;
  const feedback = document.createElement("p");
  feedback.className = "quiz-feedback";
  feedback.setAttribute("role", "status");
  explanation.before(feedback);
  for (const option of options) {
    option.setAttribute("aria-pressed", "false");
    option.addEventListener("click", () => {
      for (const choice of options) {
        choice.setAttribute("aria-pressed", String(choice === option));
      }
      const correct = option.hasAttribute("data-correct");
      feedback.textContent = correct ? "Correct." : "Try another answer.";
      explanation.hidden = !correct;
    });
  }
}
