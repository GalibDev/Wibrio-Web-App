"use client";
import Link from "next/link";
import { useState } from "react";
import {
  ArrowRight,
  ShieldCheck,
  Mic,
  MicOff,
  Video,
  VideoOff,
  PhoneOff,
  Volume2,
  RefreshCw,
} from "lucide-react";
import { Button } from "../ui/button";
import { Avatar, Badge, PageHeading } from "../shared";
export function Call({ video = false }: { video?: boolean }) {
  const [muted, setMuted] = useState(false);
  const [camera, setCamera] = useState(video);
  const [speaker, setSpeaker] = useState(true);
  const [ended, setEnded] = useState(false);
  return (
    <>
      <PageHeading
        title={
          video
            ? "Face to face. A little closer."
            : "Some things are better said."
        }
        description="Call interface preview · No microphone or camera is accessed."
      />
      <section className={`call-stage ${video ? "video-stage" : ""}`}>
        <Badge tone="hero-badge">
          SIMULATED {video ? "VIDEO" : "VOICE"} CALL
        </Badge>
        <div className="call-person">
          <Avatar initials="PS" color="mint" large />
          <h2>Priya Shah</h2>
          <p>
            {ended ? "Preview ended" : "Preview only · No real call connected"}
          </p>
          <span>
            {ended
              ? "You can restart the preview below."
              : "Your voice and video are not being transmitted."}
          </span>
        </div>
        {video && (
          <div className="self-video">
            <VideoOff size={22} />
            <span>
              {camera ? "Camera preview placeholder" : "Camera preview off"}
            </span>
            <small>No camera access</small>
          </div>
        )}
        <div className="call-controls">
          <button
            aria-label={muted ? "Unmute preview" : "Mute preview"}
            aria-pressed={muted}
            disabled={ended}
            onClick={() => setMuted(!muted)}
          >
            {muted ? <MicOff /> : <Mic />}
            <span>{muted ? "Unmute" : "Mute"}</span>
          </button>
          {video && (
            <button
              aria-label="Toggle camera placeholder"
              aria-pressed={camera}
              disabled={ended}
              onClick={() => setCamera(!camera)}
            >
              {camera ? <Video /> : <VideoOff />}
              <span>Camera</span>
            </button>
          )}
          <button
            aria-label="Toggle speaker preview"
            aria-pressed={speaker}
            disabled={ended}
            onClick={() => setSpeaker(!speaker)}
          >
            <Volume2 />
            <span>{speaker ? "Speaker on" : "Speaker off"}</span>
          </button>
          <button
            className="end-call"
            aria-label={ended ? "Restart call preview" : "End call preview"}
            onClick={() => setEnded(!ended)}
          >
            {ended ? <RefreshCw /> : <PhoneOff />}
            <span>{ended ? "Restart" : "End preview"}</span>
          </button>
        </div>
      </section>
      <div className="call-note">
        <ShieldCheck size={17} />
        Real calls require an integrated provider, signaling, and explicit
        device permissions.
      </div>
      <Button variant="secondary" asChild>
        <Link href="/conversations/priya">
          Back to conversation
          <ArrowRight size={17} />
        </Link>
      </Button>
    </>
  );
}
