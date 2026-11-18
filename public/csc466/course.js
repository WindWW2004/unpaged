const slides = [...document.querySelectorAll("body > section.slide")];
const controls = document.querySelector(".course-controls");
const picker = document.getElementById("slide-picker");
const previous = document.getElementById("previous-slide");
const next = document.getElementById("next-slide");
const position = document.getElementById("slide-position");
let activeSlide = 0;

slides.forEach((slide, index) => {
  const title = slide.querySelector("h1, h2")?.textContent.trim();
  picker.add(new Option(title ? `${index + 1}. ${title}` : `Slide ${index + 1}`, String(index)));
});

function showSlide(index) {
  slides[activeSlide].hidden = true;
  activeSlide = index;
  slides[index].hidden = false;
  picker.value = String(index);
  position.textContent = `${index + 1} of ${slides.length}`;
  previous.disabled = index === 0;
  next.disabled = index === slides.length - 1;
  window.scrollTo(0, 0);
}

function goToSlide(index) {
  if (index < 0 || index >= slides.length || index === activeSlide) return;
  window.history.pushState(null, "", `#slide-${index + 1}`);
  showSlide(index);
}

function restoreSlide() {
  const match = /^#slide-(\d+)$/.exec(window.location.hash);
  const index = match ? Number(match[1]) - 1 : 0;
  showSlide(index >= 0 && index < slides.length ? index : 0);
}

if (slides.length) {
  slides.forEach((slide) => { slide.hidden = true; });
  restoreSlide();
  controls.hidden = false;
  previous.addEventListener("click", () => goToSlide(activeSlide - 1));
  next.addEventListener("click", () => goToSlide(activeSlide + 1));
  picker.addEventListener("change", () => goToSlide(Number(picker.value)));
  window.addEventListener("hashchange", restoreSlide);
  document.addEventListener("keydown", (event) => {
    if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey ||
        event.target.closest("select, input, textarea, [contenteditable], .table-scroll")) return;
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    goToSlide(activeSlide + (event.key === "ArrowRight" ? 1 : -1));
  });
}

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
