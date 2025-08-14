import { useState, useEffect } from 'react';

export const useMaximized = () => {
  const [isMaximized, setIsMaximized] = useState<boolean>(false);

  useEffect(() => {
    const checkMaximized = () => {
      // Check if the window is maximized by checking the iframe container size
      const container = document.getElementById('iframe-container');
      if (container) {
        const rect = container.getBoundingClientRect();
        // Consider maximized if width is greater than 800px
        setIsMaximized(rect.width > 800);
      }
    };

    // Check initially
    checkMaximized();

    // Add resize listener
    window.addEventListener('resize', checkMaximized);

    // Cleanup
    return () => {
      window.removeEventListener('resize', checkMaximized);
    };
  }, []);

  return isMaximized;
};
