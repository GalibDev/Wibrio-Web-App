"use client";
import { useRef, useState } from "react";
import {
  FolderUp,
  Plus,
  SlidersHorizontal,
  Download,
  FileText,
  Image as ImageIcon,
  File,
  ChevronRight,
  X,
} from "lucide-react";
import { Button } from "../ui/button";
import { Card, PageHeading, SectionTitle, SimulationNote } from "../shared";
type LocalFile = {
  id: string;
  name: string;
  size: number;
  type: string;
  file: globalThis.File;
};
export function Files() {
  const [files, setFiles] = useState<LocalFile[]>([]);
  const [filter, setFilter] = useState("All files");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const input = useRef<HTMLInputElement>(null);
  function selectFiles(selected: FileList | null) {
    if (!selected) return;
    const added: LocalFile[] = [];
    let rejected = false;
    for (const file of Array.from(selected)) {
      if (file.size > 25 * 1024 * 1024) {
        rejected = true;
        continue;
      }
      added.push({
        id: crypto.randomUUID(),
        name: file.name,
        size: file.size,
        type: file.type,
        file,
      });
    }
    setError(
      rejected ? "Some files exceeded the 25 MB local preview limit." : "",
    );
    setFiles((old) => [...old, ...added]);
    setNotice(
      added.length
        ? "Files added for this session only. Nothing was transmitted."
        : "",
    );
  }
  function download(file: LocalFile) {
    const url = URL.createObjectURL(file.file);
    const a = document.createElement("a");
    a.href = url;
    a.download = file.name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  const shown = files.filter(
    (f) =>
      filter === "All files" ||
      (filter === "Images"
        ? f.type.startsWith("image/")
        : !f.type.startsWith("image/")),
  );
  return (
    <>
      <PageHeading
        title="A little sharing goes a long way."
        description="Explore local file selection. Real peer transfers are planned for a future phase."
      />
      <div
        className="upload-zone"
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          selectFiles(e.dataTransfer.files);
        }}
      >
        <span className="big-icon">
          <FolderUp size={30} />
        </span>
        <h2>Bring something worth sharing.</h2>
        <p>Drop files here, or choose them from your device.</p>
        <Button onClick={() => input.current?.click()}>
          <Plus size={17} />
          Choose local files
        </Button>
        <input
          ref={input}
          type="file"
          multiple
          className="sr-only"
          aria-label="Choose local files"
          onChange={(e) => {
            selectFiles(e.target.files);
            e.target.value = "";
          }}
        />
        <small>
          Up to 25 MB each · Session preview only · No upload or transmission
        </small>
      </div>
      {error && (
        <p className="error-message" role="alert">
          {error}
        </p>
      )}
      {notice && (
        <p className="success-message" role="status">
          {notice}
        </p>
      )}
      <div className="file-categories">
        {[
          { name: "All files", icon: FolderUp, color: "blue" },
          { name: "Images", icon: ImageIcon, color: "purple" },
          { name: "Documents", icon: FileText, color: "mint" },
        ].map((c) => (
          <button
            key={c.name}
            className={`card file-category ${filter === c.name ? "chosen" : ""}`}
            onClick={() => setFilter(c.name)}
            aria-pressed={filter === c.name}
          >
            <span className={`icon-tile ${c.color}`}>
              <c.icon size={23} />
            </span>
            <strong>{c.name}</strong>
            <ChevronRight size={17} />
          </button>
        ))}
      </div>
      <Card>
        <SectionTitle title="Local file previews" />
        {shown.length ? (
          shown.map((f) => (
            <div className="person-row" key={f.id}>
              <span className="icon-tile blue">
                <File size={20} />
              </span>
              <div className="file-name">
                <strong>{f.name}</strong>
                <small>{(f.size / 1024).toFixed(1)} KB · Not sent</small>
              </div>
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Download ${f.name}`}
                onClick={() => download(f)}
              >
                <Download size={18} />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Remove ${f.name}`}
                onClick={() =>
                  setFiles((old) => old.filter((item) => item.id !== f.id))
                }
              >
                <X size={18} />
              </Button>
            </div>
          ))
        ) : (
          <Empty
            title="A fresh space for your files"
            description="Choose a file to preview it here. Files are kept in memory until you leave this page."
          />
        )}
      </Card>
      <SimulationNote />
    </>
  );
}
function Empty({ title, description }: { title: string; description: string }) {
  return (
    <div className="empty-state">
      <span className="icon-tile blue">
        <SlidersHorizontal size={23} />
      </span>
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}
