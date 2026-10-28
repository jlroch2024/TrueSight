// A number that counts to its new value rather than jumping; for a visitor who asks for less motion, it just changes.
import { animate, motion, useMotionValue, useTransform } from 'motion/react';
import { useEffect } from 'react';
import { prefersStill } from './motion';

export function Count({ value, decimals = 0, suffix = '' }: { value: number; decimals?: number; suffix?: string }) {
  const motionValue = useMotionValue(value);
  const text = useTransform(motionValue, (latest) => `${latest.toFixed(decimals)}${suffix}`);
  useEffect(() => {
    if (prefersStill()) {
      motionValue.set(value);
      return;
    }
    const controls = animate(motionValue, value, { duration: 0.7, ease: [0.22, 1, 0.36, 1] });
    return () => controls.stop();
  }, [value, motionValue]);
  return <motion.span className="num">{text}</motion.span>;
}
