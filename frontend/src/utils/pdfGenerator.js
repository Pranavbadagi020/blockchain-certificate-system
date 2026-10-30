import { jsPDF } from "jspdf";
import QRCode from "qrcode";

/**
 * Constructs the canonical verification URL for a certificate.
 */
export function getVerificationUrl(certificateId) {
  const origin =
    typeof window !== "undefined" && window.location?.origin
      ? window.location.origin
      : "http://localhost:5173";
  return `${origin}/verify?id=${encodeURIComponent(certificateId || "")}`;
}

/**
 * Generates a base64 PNG data URL of the verification QR code.
 */
export async function generateQRCodeDataUrl(url) {
  try {
    return await QRCode.toDataURL(url, {
      width: 320,
      margin: 1,
      color: {
        dark: "#0F172A",
        light: "#FFFFFF",
      },
      errorCorrectionLevel: "M",
    });
  } catch (error) {
    console.error("Failed to generate QR code data URL:", error);
    return null;
  }
}

/**
 * Builds an academic landscape PDF certificate using jsPDF.
 *
 * @param {Object} certificate The certificate data object
 * @returns {Promise<jsPDF>} The configured jsPDF instance
 */
export async function generateCertificatePDF(certificate) {
  const certId = certificate.certificateId || certificate.id || "CERT-UNKNOWN";
  const studentName = certificate.studentName || certificate.holderName || "Recipient";
  const rollNumber = certificate.studentRollNumber || certificate.holderEmail || "—";
  const course = certificate.course || "Course of Study";
  const institution = certificate.institution || "Issuing Institution";
  const issueDate = certificate.issueDate || "—";
  const grade = certificate.grade ? String(certificate.grade).trim() : "";
  const certHash = certificate.certificateHash || "Not recorded";
  const txHash = certificate.transactionHash || certificate.txHash || "Not recorded";

  const doc = new jsPDF({
    orientation: "landscape",
    unit: "mm",
    format: "a4",
  });

  doc.setProperties({
    title: certId,
    subject: "Official Academic Certificate",
    author: institution,
    keywords: `${certId}, ${certHash}`,
    creator: "CertChain Blockchain Verification System",
  });

  const width = doc.internal.pageSize.getWidth(); // 297 mm
  const height = doc.internal.pageSize.getHeight(); // 210 mm

  // Background tint
  doc.setFillColor(252, 253, 255);
  doc.rect(0, 0, width, height, "F");

  // Outer primary border (Deep Navy)
  doc.setDrawColor(15, 23, 42); // #0F172A
  doc.setLineWidth(1.4);
  doc.rect(8, 8, width - 16, height - 16);

  // Inner decorative border (Teal)
  doc.setDrawColor(13, 148, 136); // #0D9488
  doc.setLineWidth(0.5);
  doc.rect(11, 11, width - 22, height - 22);

  // Decorative corner accent notches
  doc.setFillColor(217, 119, 6); // Amber / Gold accent
  const cornerSize = 4;
  doc.rect(11, 11, cornerSize, cornerSize, "F");
  doc.rect(width - 11 - cornerSize, 11, cornerSize, cornerSize, "F");
  doc.rect(11, height - 11 - cornerSize, cornerSize, cornerSize, "F");
  doc.rect(width - 11 - cornerSize, height - 11 - cornerSize, cornerSize, cornerSize, "F");

  // Header: Institution Name
  doc.setFont("helvetica", "bold");
  doc.setFontSize(21);
  doc.setTextColor(15, 23, 42);
  const instLines = doc.splitTextToSize(institution.toUpperCase(), width - 50);
  doc.text(instLines, width / 2, 28, { align: "center" });

  // Subtitle: Certificate of Achievement
  doc.setFontSize(11);
  doc.setTextColor(13, 148, 136);
  doc.text("OFFICIAL BLOCKCHAIN-BACKED CERTIFICATE OF ACHIEVEMENT", width / 2, 38, { align: "center" });

  // Gold divider line
  doc.setDrawColor(217, 119, 6);
  doc.setLineWidth(0.7);
  doc.line(width / 2 - 45, 42, width / 2 + 45, 42);

  // Body: Certification text
  doc.setFont("helvetica", "italic");
  doc.setFontSize(11);
  doc.setTextColor(71, 85, 105);
  doc.text("This is to certify that", width / 2, 53, { align: "center" });

  // Student Name
  doc.setFont("helvetica", "bold");
  doc.setFontSize(23);
  doc.setTextColor(15, 23, 42);
  doc.text(studentName, width / 2, 65, { align: "center" });

  // Underline beneath name
  doc.setDrawColor(203, 213, 225);
  doc.setLineWidth(0.4);
  const nameWidth = doc.getTextWidth(studentName);
  const lineHalf = Math.max(nameWidth / 2 + 8, 35);
  doc.line(width / 2 - lineHalf, 68, width / 2 + lineHalf, 68);

  // Roll Number
  doc.setFont("helvetica", "normal");
  doc.setFontSize(10.5);
  doc.setTextColor(51, 65, 85);
  doc.text(`Institutional ID / Roll Number: ${rollNumber}`, width / 2, 76, { align: "center" });

  // Programme statement
  doc.setTextColor(71, 85, 105);
  doc.text("has successfully met all academic requirements and completed the prescribed programme in", width / 2, 86, { align: "center" });

  // Course Title
  doc.setFont("helvetica", "bold");
  doc.setFontSize(16);
  doc.setTextColor(13, 148, 136);
  const courseLines = doc.splitTextToSize(course, width - 60);
  doc.text(courseLines, width / 2, 96, { align: "center" });

  // Grade / Honours
  let currentY = 104;
  if (grade && grade !== "—") {
    doc.setFont("helvetica", "normal");
    doc.setFontSize(11);
    doc.setTextColor(51, 65, 85);
    doc.text(`Grade / Classification: ${grade}`, width / 2, currentY, { align: "center" });
    currentY += 8;
  }

  // Issue Date
  doc.setFont("helvetica", "normal");
  doc.setFontSize(10.5);
  doc.setTextColor(71, 85, 105);
  doc.text(`Conferred on: ${issueDate}`, width / 2, currentY, { align: "center" });

  // Bottom section divider
  doc.setDrawColor(203, 213, 225);
  doc.setLineWidth(0.5);
  doc.line(16, 126, width - 16, 126);

  // Left metadata: Certificate ID & Hashes
  doc.setFont("helvetica", "bold");
  doc.setFontSize(8);
  doc.setTextColor(100, 116, 139);
  doc.text("OFFICIAL CERTIFICATE ID", 18, 134);

  doc.setFont("helvetica", "bold");
  doc.setFontSize(12);
  doc.setTextColor(15, 23, 42);
  doc.text(certId, 18, 140);

  doc.setFont("helvetica", "bold");
  doc.setFontSize(7.5);
  doc.setTextColor(100, 116, 139);
  doc.text("SHA-256 CONTENT HASH:", 18, 148);

  doc.setFont("courier", "normal");
  doc.setFontSize(7);
  doc.setTextColor(51, 65, 85);
  doc.text(certHash, 18, 153);

  doc.setFont("helvetica", "bold");
  doc.setFontSize(7.5);
  doc.setTextColor(100, 116, 139);
  doc.text("BLOCKCHAIN TRANSACTION HASH:", 18, 161);

  doc.setFont("courier", "normal");
  doc.setFontSize(7);
  doc.setTextColor(51, 65, 85);
  doc.text(txHash, 18, 166);

  doc.setFont("helvetica", "normal");
  doc.setFontSize(7.5);
  doc.setTextColor(13, 148, 136);
  doc.text("Cryptographically anchored to Ethereum Smart Contract | Tamper-evident digital record", 18, 177);

  // Right metadata: Unique QR code
  const verificationUrl = getVerificationUrl(certId);
  const qrDataUrl = await generateQRCodeDataUrl(verificationUrl);

  const qrX = width - 56;
  const qrY = 132;
  const qrSize = 38;

  if (qrDataUrl) {
    doc.addImage(qrDataUrl, "PNG", qrX, qrY, qrSize, qrSize);
  }

  doc.setFont("helvetica", "bold");
  doc.setFontSize(8);
  doc.setTextColor(13, 148, 136);
  doc.text("SCAN TO VERIFY", qrX + qrSize / 2, 175, { align: "center" });

  doc.setFont("helvetica", "normal");
  doc.setFontSize(6.5);
  doc.setTextColor(100, 116, 139);
  doc.text("Instant online verification", qrX + qrSize / 2, 179, { align: "center" });

  return doc;
}

/**
 * Generates and triggers the browser download of the certificate PDF.
 *
 * @param {Object} certificate The certificate data object
 */
export async function downloadCertificatePDF(certificate) {
  if (!certificate) return;
  const doc = await generateCertificatePDF(certificate);
  const safeId = (certificate.certificateId || certificate.id || "certificate").replace(/[^a-zA-Z0-9_-]/g, "_");
  doc.save(`Certificate_${safeId}.pdf`);
}

/**
 * Generates a preview Blob URL for the certificate PDF.
 *
 * @param {Object} certificate The certificate data object
 * @returns {Promise<string|null>} Blob URL for viewing the PDF in an iframe or viewer
 */
export async function generateCertificatePreviewUrl(certificate) {
  if (!certificate) return null;
  const doc = await generateCertificatePDF(certificate);
  return doc.output("bloburl");
}
