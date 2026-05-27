import {
  Github, Linkedin, Twitter, Facebook, Instagram, Youtube, Globe, Mail,
  Phone, MapPin, Briefcase, Link, ExternalLink, Twitch, Gitlab, Dribbble,
  Figma, Codepen, Slack, Code, Terminal, PenTool, MessageCircle, Gamepad2,
  Hash, Layers,
} from 'lucide-angular';

export interface SocialPlatform {
  platform: string;
  iconKey: string;
  svgPath: string; // retained for PDF rendering (openhtmltopdf/Java backend)
}

export interface SocialIcon {
  key: string;
  label: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  icon: any;
}

export const SOCIAL_PLATFORMS: SocialPlatform[] = [
  {
    platform: 'LinkedIn',
    iconKey: 'linkedin',
    svgPath: 'M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 01-2.063-2.065 2.064 2.064 0 112.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z',
  },
  {
    platform: 'GitHub',
    iconKey: 'github',
    svgPath: 'M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12',
  },
  {
    platform: 'Website',
    iconKey: 'globe',
    svgPath: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z',
  },
  {
    platform: 'Twitter / X',
    iconKey: 'twitter',
    svgPath: 'M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-4.714-6.231-5.401 6.231H2.744l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z',
  },
  {
    platform: 'Facebook',
    iconKey: 'facebook',
    svgPath: 'M18 2h-3a5 5 0 0 0-5 5v3H7v4h3v8h4v-8h3l1-4h-4V7a1 1 0 0 1 1-1h3z',
  },
  {
    platform: 'Instagram',
    iconKey: 'instagram',
    svgPath: 'M7.8 2h8.4C19.4 2 22 4.6 22 7.8v8.4a5.8 5.8 0 0 1-5.8 5.8H7.8C4.6 22 2 19.4 2 16.2V7.8A5.8 5.8 0 0 1 7.8 2m-.2 2A3.6 3.6 0 0 0 4 7.6v8.8C4 18.39 5.61 20 7.6 20h8.8a3.6 3.6 0 0 0 3.6-3.6V7.6C20 5.61 18.39 4 16.4 4H7.6m9.65 1.5a1.25 1.25 0 0 1 1.25 1.25A1.25 1.25 0 0 1 17.25 8 1.25 1.25 0 0 1 16 6.75a1.25 1.25 0 0 1 1.25-1.25M12 7a5 5 0 0 1 5 5 5 5 0 0 1-5 5 5 5 0 0 1-5-5 5 5 0 0 1 5-5m0 2a3 3 0 0 0-3 3 3 3 0 0 0 3 3 3 3 0 0 0 3-3 3 3 0 0 0-3-3z',
  },
  {
    platform: 'YouTube',
    iconKey: 'youtube',
    svgPath: 'M2.5 17a24.12 24.12 0 0 1 0-10 2 2 0 0 1 1.4-1.4 49.56 49.56 0 0 1 16.2 0A2 2 0 0 1 21.5 7a24.12 24.12 0 0 1 0 10 2 2 0 0 1-1.4 1.4 49.55 49.55 0 0 1-16.2 0A2 2 0 0 1 2.5 17zm7.5-2 5-3-5-3z',
  },
  {
    platform: 'Twitch',
    iconKey: 'twitch',
    svgPath: 'M21 2H3v16h5v4l4-4h5l4-4V2zm-10 9V7m5 4V7',
  },
  {
    platform: 'GitLab',
    iconKey: 'gitlab',
    svgPath: 'm22 13.29-3.33-10a.42.42 0 0 0-.14-.18.38.38 0 0 0-.22-.11.39.39 0 0 0-.23.07.42.42 0 0 0-.14.18l-2.26 6.67H8.32L6.1 3.26a.42.42 0 0 0-.1-.18.38.38 0 0 0-.26-.08.39.39 0 0 0-.23.07.42.42 0 0 0-.14.18L2 13.29a.74.74 0 0 0 .27.83L12 21l9.69-6.88a.71.71 0 0 0 .31-.83Z',
  },
  {
    platform: 'Medium',
    iconKey: 'globe',
    svgPath: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z',
  },
  {
    platform: 'Discord',
    iconKey: 'message-circle',
    svgPath: 'M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719',
  },
  {
    platform: 'Reddit',
    iconKey: 'hash',
    svgPath: 'M4 9h16M4 15h16M10 3L8 21M16 3L14 21',
  },
  {
    platform: 'LeetCode',
    iconKey: 'code',
    svgPath: 'm16 18 6-6-6-6M8 6l-6 6 6 6',
  },
  {
    platform: 'Kaggle',
    iconKey: 'layers',
    svgPath: 'M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z',
  },
  {
    platform: 'Behance',
    iconKey: 'figma',
    svgPath: 'M22 7h-7v-2h7v2zm1.726 10c-.442 1.297-2.029 3-5.101 3-3.074 0-5.564-1.729-5.564-5.675 0-3.91 2.325-5.92 5.466-5.92 3.082 0 4.964 1.782 5.375 4.426.078.506.109 1.188.095 2.14H15.97c.13 3.211 3.483 3.312 4.588 2.029H23.726zm-7.726-5.968c-1.248 0-2.086.63-2.409 1.924h4.578c-.131-1.007-.68-1.924-2.169-1.924zM4.918 11.107c1.078 0 1.887-.334 1.887-1.344 0-1.033-.804-1.355-1.887-1.355H2.459v2.699h2.459zm-.137 4.107c1.143 0 2.014-.393 2.014-1.594 0-1.199-.914-1.562-2.014-1.562H2.459v3.156h2.322zM0 6h4.918C6.93 6 8.5 6.701 8.5 8.856c0 1.226-.553 2.006-1.46 2.488 1.233.394 2.021 1.261 2.021 2.762 0 2.431-1.969 3.272-4.152 3.272H0V6z',
  },
  {
    platform: 'Dribbble',
    iconKey: 'dribbble',
    svgPath: 'M12 24C5.385 24 0 18.615 0 12S5.385 0 12 0s12 5.385 12 12-5.385 12-12 12zm10.12-10.358c-.35-.11-3.17-.953-6.384-.438 1.34 3.684 1.887 6.684 1.992 7.308 2.3-1.555 3.936-4.02 4.395-6.87zm-6.115 7.808c-.153-.9-.75-4.032-2.19-7.77l-.066.02c-5.79 2.015-7.86 6.025-8.048 6.39 1.73 1.35 3.92 2.165 6.298 2.165 1.42 0 2.77-.29 4.006-.805zm-9.187-2.428c.23-.48 3.145-5.97 8.337-7.767.14-.045.28-.088.42-.128-.27-.615-.57-1.228-.888-1.83C9.26 11.387 4.705 11.457 4.272 11.45l-.003.06c0 2.8 1.067 5.355 2.646 7.32zm-3.23-8.945c.44.012 4.353.05 8.188-1.086A68.494 68.494 0 0 0 6.718 4.72C4.948 6.15 3.703 8.246 2.688 13.077zM9.666 4.03c1.24 1.563 2.45 3.27 3.19 5.055 3.226-1.21 4.594-3.042 4.752-3.264-1.49-1.328-3.43-2.134-5.554-2.134-.47 0-.932.05-1.388.143zm4.24 6.967c-.36-.85-.78-1.7-1.25-2.53-3.154 1.02-7.054 1.23-7.16 1.24-.01.1-.01.2-.01.3 0 2.19.82 4.19 2.16 5.71 1.04-1.91 3.57-4.47 6.26-4.72z',
  },
  {
    platform: 'Stack Overflow',
    iconKey: 'hash',
    svgPath: 'M15.725 0l-1.72 1.277 6.39 8.588 1.716-1.277L15.725 0zm-3.94 3.418l-1.369 1.644 8.225 6.85 1.369-1.644-8.225-6.85zm-3.15 4.465l-.905 1.94 9.702 4.517.904-1.94-9.701-4.517zm-1.85 4.86l-.44 2.093 10.473 2.201.44-2.092-10.473-2.203zM1.89 15.47V24h19.19v-8.53h-2.133v6.397H4.021v-6.396H1.89zm4.265 2.133v2.13h10.66v-2.13H6.154Z',
  },
  {
    platform: 'Email',
    iconKey: 'mail',
    svgPath: 'M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z',
  },
  {
    platform: 'Other',
    iconKey: 'link',
    svgPath: 'M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71',
  },
];

export const SOCIAL_PLATFORM_MAP = new Map(
  SOCIAL_PLATFORMS.map(p => [p.iconKey, p])
);

export function getSocialPlatformIcon(iconKey: string): string {
  return SOCIAL_PLATFORM_MAP.get(iconKey)?.svgPath ?? '';
}

/** 26-icon library shown in the icon override picker. */
export const SOCIAL_ICON_LIBRARY: SocialIcon[] = [
  { key: 'github',         label: 'GitHub',        icon: Github },
  { key: 'linkedin',       label: 'LinkedIn',       icon: Linkedin },
  { key: 'twitter',        label: 'Twitter/X',      icon: Twitter },
  { key: 'facebook',       label: 'Facebook',       icon: Facebook },
  { key: 'instagram',      label: 'Instagram',      icon: Instagram },
  { key: 'youtube',        label: 'YouTube',        icon: Youtube },
  { key: 'globe',          label: 'Globe',          icon: Globe },
  { key: 'mail',           label: 'Email',          icon: Mail },
  { key: 'phone',          label: 'Phone',          icon: Phone },
  { key: 'map-pin',        label: 'Location',       icon: MapPin },
  { key: 'briefcase',      label: 'Portfolio',      icon: Briefcase },
  { key: 'link',           label: 'Link',           icon: Link },
  { key: 'external-link',  label: 'External',       icon: ExternalLink },
  { key: 'twitch',         label: 'Twitch',         icon: Twitch },
  { key: 'gitlab',         label: 'GitLab',         icon: Gitlab },
  { key: 'dribbble',       label: 'Dribbble',       icon: Dribbble },
  { key: 'figma',          label: 'Figma',          icon: Figma },
  { key: 'codepen',        label: 'CodePen',        icon: Codepen },
  { key: 'slack',          label: 'Slack',          icon: Slack },
  { key: 'code',           label: 'Code',           icon: Code },
  { key: 'terminal',       label: 'Terminal',       icon: Terminal },
  { key: 'pen-tool',       label: 'Design',         icon: PenTool },
  { key: 'message-circle', label: 'Chat',           icon: MessageCircle },
  { key: 'gamepad-2',      label: 'Gaming',         icon: Gamepad2 },
  { key: 'hash',           label: 'Forum',          icon: Hash },
  { key: 'layers',         label: 'Portfolio',      icon: Layers },
];

/** Map of all iconKeys → LucideIcon for layout rendering. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const ALL_ICON_MAP = new Map<string, any>(
  SOCIAL_ICON_LIBRARY.map(i => [i.key, i.icon])
);

/** Returns the LucideIcon for a social iconKey (used in layout templates). */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function getSocialIcon(iconKey: string): any {
  return ALL_ICON_MAP.get(iconKey) ?? Link;
}

/** Contact-info icons (email, phone, location) for resume layouts. */
export const CONTACT_ICONS: { email: string; phone: string; location: string } = {
  email: 'M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z',
  phone: 'M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z',
  location: 'M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z',
};
