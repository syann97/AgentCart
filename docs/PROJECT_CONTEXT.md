# Project Context

## Overview

AgentCart is an AI-powered backend system that supports purchase decision-making using a multi-agent architecture.

## Core Pipeline

Planner → Executor → Evaluator

## Key Features

- Hybrid Search (SQL filtering + Vector reranking)
- Explainable recommendations (conditions + review keywords)
- Role-based agent architecture

## Tech Stack

- Spring Boot 4 / Java 25
- MySQL / Redis / Kafka
- PostgreSQL (pgvector)
- Spring AI

## System Goal

- Provide reliable and explainable recommendations
- Maintain system stability even when LLM fails