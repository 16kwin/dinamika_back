-- V13__Add_rating_table.sql

CREATE TABLE IF NOT EXISTS public.reg_rating
(
    uid uuid NOT NULL,
    material_uid uuid NOT NULL,
    rating integer NOT NULL CHECK (rating >= 0 AND rating <= 5),
    comment text COLLATE pg_catalog."default",
    author text COLLATE pg_catalog."default",
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reg_rating_pkey PRIMARY KEY (uid),
    CONSTRAINT reg_rating_material_fkey FOREIGN KEY (material_uid)
        REFERENCES public.spr_material (uid) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

COMMENT ON TABLE public.reg_rating IS 'Регистр Рейтинг';
COMMENT ON COLUMN public.reg_rating.rating IS 'Оценка (0-5)';
COMMENT ON COLUMN public.reg_rating.comment IS 'Текст отзыва';
COMMENT ON COLUMN public.reg_rating.author IS 'Автор отзыва';