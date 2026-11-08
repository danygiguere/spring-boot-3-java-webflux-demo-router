package com.example.routerdemo.service;

import com.example.routerdemo.dto.PostDetails;

/** Outcome of POST /posts; {@code replayed} when the Idempotency-Key had already created this post. */
public record CreatedPost(PostDetails post, boolean replayed) {}
