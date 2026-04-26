import html2pdf from "html2pdf.js";

/**
 * Генерира .pdf од DOM на извештајот (без print дијалог).
 * @param {HTMLElement | null} element — обично `.report-document`
 * @param {string} filename
 * @returns {Promise<void>}
 */
export function downloadReportPdf(element, filename = "agro-izvestaj.pdf") {
  if (!element) {
    return Promise.reject(new Error("Нема содржина за извештај."));
  }
  const opt = {
    margin: 10,
    filename,
    image: { type: "jpeg", quality: 0.95 },
    html2canvas: {
      scale: 2,
      useCORS: true,
      logging: false,
      letterRendering: true,
    },
    jsPDF: { unit: "mm", format: "a4", orientation: "portrait" },
    pagebreak: { mode: ["avoid-all", "css", "legacy"] },
  };
  return html2pdf().set(opt).from(element).save();
}
