import type { ReactElement, ReactNode } from "react";
import { useCallback, useEffect, useState } from "react";
import { GLOBAL_ERROR_EVENT } from "../api/errors";
import GlobalErrorModal from "../components/GlobalErrorModal";

export function GlobalErrorProvider({ children }: { children: ReactNode }): ReactElement {
  const [message, setMessage] = useState("");

  useEffect(() => {
    const handleError = (event: Event): void => {
      const detail = (event as CustomEvent<string>).detail;
      if (detail) setMessage((current) => current || detail);
    };
    window.addEventListener(GLOBAL_ERROR_EVENT, handleError);
    return () => window.removeEventListener(GLOBAL_ERROR_EVENT, handleError);
  }, []);

  const close = useCallback((): void => setMessage(""), []);

  return (
    <>
      {children}
      {message ? <GlobalErrorModal message={message} onClose={close} /> : null}
    </>
  );
}
