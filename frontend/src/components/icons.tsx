// Íconos de línea simples (24×24, heredan el color del texto). Decorativos: el texto va al lado.
type IconProps = { size?: number }

function Svg({ size = 22, children }: IconProps & { children: React.ReactNode }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
      {children}
    </svg>
  )
}

export const HomeIcon = (props: IconProps) => (
  <Svg {...props}><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V21h14V9.5" /><path d="M10 21v-6h4v6" /></Svg>
)

export const CalendarIcon = (props: IconProps) => (
  <Svg {...props}><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 10h18M8 3v4M16 3v4" /></Svg>
)

/** Cancha vista desde arriba. */
export const FieldIcon = (props: IconProps) => (
  <Svg {...props}><rect x="2.5" y="5" width="19" height="14" rx="1.5" /><path d="M12 5v14" /><circle cx="12" cy="12" r="2.5" /><path d="M2.5 9.5h2.5v5H2.5M21.5 9.5H19v5h2.5" /></Svg>
)

export const UserIcon = (props: IconProps) => (
  <Svg {...props}><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7" /></Svg>
)

export const ShieldIcon = (props: IconProps) => (
  <Svg {...props}><path d="M12 3 4 6v6c0 4.5 3.4 8 8 9 4.6-1 8-4.5 8-9V6l-8-3Z" /></Svg>
)

export const LoginIcon = (props: IconProps) => (
  <Svg {...props}><path d="M14 4h5v16h-5" /><path d="M3 12h11M10 8l4 4-4 4" /></Svg>
)

export const MapPinIcon = (props: IconProps) => (
  <Svg {...props}><path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21Z" /><circle cx="12" cy="9.5" r="2.5" /></Svg>
)

export const ShareIcon = (props: IconProps) => (
  <Svg {...props}><circle cx="18" cy="5" r="2.5" /><circle cx="6" cy="12" r="2.5" /><circle cx="18" cy="19" r="2.5" /><path d="m8.2 10.8 7.6-4.4M8.2 13.2l7.6 4.4" /></Svg>
)

export const CalendarPlusIcon = (props: IconProps) => (
  <Svg {...props}><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 10h18M8 3v4M16 3v4M12 13v5M9.5 15.5h5" /></Svg>
)

export const CheckCircleIcon = (props: IconProps) => (
  <Svg {...props}><circle cx="12" cy="12" r="9" /><path d="m8 12.5 2.8 2.8L16 10" /></Svg>
)
