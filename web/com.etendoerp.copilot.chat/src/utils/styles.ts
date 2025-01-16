import { ROLE_ERROR, ROLE_USER } from "./constants";

export const getMessageContainerClasses = (sender: string) => {
  const baseClasses = "text-sm mt-[12px]";
  switch (sender) {
    case ROLE_USER:
      return `${baseClasses} text-right user-message slide-up-fade-in`;
    case "interpreting":
      return baseClasses;
    case ROLE_ERROR:
      return `${baseClasses} text-red-900 rounded-lg`;
    default:
      return `${baseClasses} text-black rounded-lg`;
  }
};