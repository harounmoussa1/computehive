"""Met docs/worker-protocol.md en PDF (à envoyer au binôme), avec la même mise en page que le guide.

Usage : python docs/guide/protocol.py  (écrit docs/ComputeHive-protocole-agent.pdf ; Python 3, mistune et Chrome requis)
"""
import datetime
import pathlib
import subprocess
import tempfile

import mistune

DOCS = pathlib.Path(__file__).resolve().parent.parent
SOURCE = DOCS / "worker-protocol.md"
OUT_PDF = DOCS / "ComputeHive-protocole-agent.pdf"
OUT_HTML = pathlib.Path(tempfile.gettempdir()) / "computehive-protocole.html"
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

CSS = r"""
@page { size: A4; margin: 16mm 15mm 17mm 15mm;
        @bottom-center { content: "ComputeHive — protocole serveur ↔ agent · " counter(page);
                         font-family: "Segoe UI", Arial, sans-serif; font-size: 8.5pt; color: #64748b; } }
* { box-sizing: border-box; }
body { font-family: "Segoe UI", Arial, sans-serif; color: #1e293b; font-size: 10pt; line-height: 1.45; margin: 0; }
.band { background: linear-gradient(135deg, #1e3a8a, #059669); color: #fff; padding: 9mm 10mm 7mm;
        border-radius: 10px; margin-bottom: 6mm; }
.band .kicker { font-size: 9.5pt; letter-spacing: 2px; text-transform: uppercase; opacity: .85; }
.band .title { font-size: 22pt; font-weight: 700; margin: 2mm 0 1.5mm; }
.band .sub { font-size: 10.5pt; opacity: .95; }
h1 { display: none; }
h2 { font-size: 14pt; color: #1e3a8a; margin: 7mm 0 2.5mm; padding-bottom: 1.5mm; border-bottom: 2px solid #2563eb;
     break-after: avoid; }
h3 { font-size: 11pt; color: #1e40af; margin: 5mm 0 1.5mm; break-after: avoid; }
p { margin: 1.5mm 0 2.5mm; }
code { font-family: Consolas, "Cascadia Mono", monospace; font-size: 8.8pt; background: #f1f5f9; padding: 0 3px;
       border-radius: 3px; color: #0f172a; }
pre { font-family: Consolas, monospace; font-size: 8.3pt; line-height: 1.35; background: #0f172a; color: #e2e8f0;
      padding: 3mm 4mm; border-radius: 6px; white-space: pre-wrap; break-inside: avoid; }
pre code { background: none; color: inherit; padding: 0; font-size: inherit; }
table { width: 100%; border-collapse: collapse; margin: 2mm 0 4mm; font-size: 9pt; break-inside: avoid; }
th { background: #1e3a8a; color: #fff; text-align: left; padding: 1.5mm 2mm; font-weight: 600; }
td { border-bottom: 1px solid #e2e8f0; padding: 1.4mm 2mm; vertical-align: top; }
tr:nth-child(even) td { background: #f8fafc; }
blockquote { border-left: 4px solid #2563eb; background: #eff6ff; margin: 3mm 0; padding: 1.5mm 4mm;
             border-radius: 0 6px 6px 0; break-inside: avoid; }
blockquote table { background: #fff; }
ul, ol { margin: 1mm 0 3mm; padding-left: 6mm; }
li { margin: 0.6mm 0; }
hr { border: none; border-top: 1px solid #e2e8f0; margin: 5mm 0; }
"""


def render():
    markdown = mistune.create_markdown(plugins=["table", "strikethrough"], escape=False)
    body = markdown(SOURCE.read_text(encoding="utf-8"))
    today = datetime.date.today().strftime("%d/%m/%Y")
    band = ('<div class="band"><div class="kicker">ComputeHive · contrat commun serveur ↔ agent</div>'
            '<div class="title">Le serveur face aux agents</div>'
            '<div class="sub">docs/worker-protocol.md — à lire avec contracts/worker-proto/src/main/proto/worker.proto'
            f' · version du {today}</div></div>')
    html = ('<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>ComputeHive — protocole agent</title>'
            f'<style>{CSS}</style></head><body>{band}{body}</body></html>')
    OUT_HTML.write_text(html, encoding="utf-8")


def pdf():
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    f"--print-to-pdf={OUT_PDF}", OUT_HTML.as_uri()], check=True, capture_output=True, timeout=120)


if __name__ == "__main__":
    render()
    pdf()
    print(OUT_PDF, OUT_PDF.stat().st_size)
