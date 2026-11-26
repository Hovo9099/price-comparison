import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { hy, type Strings } from './hy';
import { en } from './en';

export type Language = 'hy' | 'en';
const KEY = 'language';
const dictionaries: Record<Language, Strings> = { hy, en };

interface I18n {
  t: Strings;
  language: Language;
  setLanguage: (l: Language) => void;
}

const I18nContext = createContext<I18n>({ t: hy, language: 'hy', setLanguage: () => undefined });

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const [language, setLang] = useState<Language>('hy');
  useEffect(() => {
    AsyncStorage.getItem(KEY)
      .then((v) => {
        if (v === 'hy' || v === 'en') setLang(v);
      })
      .catch(() => undefined);
  }, []);
  const value = useMemo<I18n>(
    () => ({
      t: dictionaries[language],
      language,
      setLanguage: (l) => {
        setLang(l);
        AsyncStorage.setItem(KEY, l).catch(() => undefined);
      },
    }),
    [language],
  );
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export const useI18n = () => useContext(I18nContext);

/** Product name in the current language, falling back to Armenian. */
export function productName(p: { name: string; nameEn?: string | null }, language: Language): string {
  return language === 'en' && p.nameEn ? p.nameEn : p.name;
}
