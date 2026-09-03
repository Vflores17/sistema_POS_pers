import type { CSSProperties, ReactElement } from "react";
import styles from "./SkeletonBlock.module.css";

interface SkeletonBlockProps {
  className?: string;
  style?: CSSProperties;
}

export default function SkeletonBlock({ className = "", style }: SkeletonBlockProps): ReactElement {
  return <span className={`${styles.skeleton} ${className}`} style={style} aria-hidden="true" />;
}
