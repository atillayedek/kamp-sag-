-- get_advisors (performance) bulgusu: 13 foreign key'in kapsayan indeksi yoktu.
create index campus_event_attendees_user_id_idx on public.campus_event_attendees (user_id);
create index campus_events_created_by_idx on public.campus_events (created_by);
create index comments_author_id_idx on public.comments (author_id);
create index conversations_related_post_id_idx on public.conversations (related_post_id);
create index conversations_related_requirement_id_idx on public.conversations (related_requirement_id);
create index leaderboard_entries_university_id_idx on public.leaderboard_entries (university_id);
create index messages_sender_id_idx on public.messages (sender_id);
create index post_likes_user_id_idx on public.post_likes (user_id);
create index reports_reporter_id_idx on public.reports (reporter_id);
create index reports_reviewed_by_idx on public.reports (reviewed_by);
create index saved_posts_user_id_idx on public.saved_posts (user_id);
create index student_verifications_reviewed_by_idx on public.student_verifications (reviewed_by);
create index user_badges_badge_id_idx on public.user_badges (badge_id);
