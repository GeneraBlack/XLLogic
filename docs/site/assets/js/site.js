document.addEventListener("DOMContentLoaded", () => {
    const navToggle = document.querySelector(".nav-toggle");
    const nav = document.querySelector(".site-nav");

    if (navToggle && nav) {
        navToggle.addEventListener("click", () => {
            const open = nav.classList.toggle("is-open");
            navToggle.setAttribute("aria-expanded", String(open));
        });
    }

    document.querySelectorAll(".copy-code").forEach((button) => {
        button.addEventListener("click", async () => {
            const targetId = button.dataset.copyTarget;
            const target = targetId ? document.getElementById(targetId) : null;
            if (!target) {
                return;
            }

            try {
                await navigator.clipboard.writeText(target.textContent || "");
                const previous = button.textContent;
                button.textContent = "Copied";
                globalThis.setTimeout(() => {
                    button.textContent = previous || "Copy";
                }, 1400);
            } catch {
                button.textContent = "Unavailable";
                globalThis.setTimeout(() => {
                    button.textContent = "Copy";
                }, 1400);
            }
        });
    });

    const filterButtons = Array.from(document.querySelectorAll(".filter-button"));
    const blockCards = Array.from(document.querySelectorAll(".block-card[data-category]"));

    if (filterButtons.length > 0 && blockCards.length > 0) {
        filterButtons.forEach((button) => {
            button.addEventListener("click", () => {
                const filter = button.dataset.filter || "all";
                filterButtons.forEach((candidate) => candidate.classList.remove("is-active"));
                button.classList.add("is-active");

                blockCards.forEach((card) => {
                    const category = card.dataset.category || "";
                    const visible = filter === "all" || category.split(" ").includes(filter);
                    card.classList.toggle("hidden", !visible);
                });
            });
        });
    }

    const revealNodes = document.querySelectorAll(".reveal");
    if (revealNodes.length > 0 && "IntersectionObserver" in globalThis) {
        const observer = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                if (entry.isIntersecting) {
                    entry.target.classList.add("is-visible");
                    observer.unobserve(entry.target);
                }
            });
        }, { threshold: 0.16 });

        revealNodes.forEach((node) => observer.observe(node));
    } else {
        revealNodes.forEach((node) => node.classList.add("is-visible"));
    }
});
